/*
	This file is part of jConSGapMiner - see https://github.com/slide-lig/jConSGapMiner
	
	Copyright 2014 Vincent Leroy, Université Joseph Fourier and CNRS

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	 http://www.apache.org/licenses/LICENSE-2.0
	 
	or see the LICENSE.txt file joined with this program.

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package fr.liglab.consgap.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class DepthFirstThread extends Thread {
	private final ReadWriteLock lock;
	private final List<MiningStep> stackedJobs;
	private final int id;
	private final List<DepthFirstThread> threads;

	public DepthFirstThread(final int id, List<DepthFirstThread> threads) {
		super("MiningThread" + id);
		this.threads = threads;
		this.stackedJobs = new ArrayList<MiningStep>();
		this.id = id;
		this.lock = new ReentrantReadWriteLock();
	}

	public void init(MiningStep initState) {
		this.stackedJobs.add(initState);
	}

	@Override
	public long getId() {
		return this.id;
	}

	@Override
	public void run() {
		// no need to readlock, this thread is the only one that can do
		// writes
		boolean exit = false;
		while (!exit) {
			MiningStep sj = null;
			if (!this.stackedJobs.isEmpty()) {
				sj = this.stackedJobs.get(this.stackedJobs.size() - 1);
				MiningStep extended = sj.next();
				// iterator is finished, remove it from the stack
				if (extended == null) {
					this.lock.writeLock().lock();
					this.stackedJobs.remove(this.stackedJobs.size() - 1);
					this.lock.writeLock().unlock();
				} else {
					this.queueTask(extended);
				}

			} else { // our list was empty, we should steal from another
						// thread
				MiningStep stolj = stealJob();
				if (stolj == null) {
					exit = true;
				} else {
					queueTask(stolj);
				}
			}
		}
		// System.out.println(this + " terminated");
	}

	private void queueTask(MiningStep state) {
		this.lock.writeLock().lock();
		this.stackedJobs.add(state);
		this.lock.writeLock().unlock();
	}

	private MiningStep stealJob() {
		// here we need to readlock because the owner thread can write
		for (DepthFirstThread victim : this.threads) {
			if (victim != this) {
				MiningStep e = this.stealJob(victim);
				if (e != null) {
					return e;
				}
			}
		}
		return null;
	}

	private MiningStep stealJob(DepthFirstThread victim) {
		victim.lock.readLock().lock();
		for (int stealPos = 0; stealPos < victim.stackedJobs.size(); stealPos++) {
			MiningStep sj = victim.stackedJobs.get(stealPos);
			MiningStep next = sj.next();

			if (next != null) {
				// System.out.println(this + " stole from " + victim + " level "
				// + next.getLevel());
				this.queueTask(sj);
				victim.lock.readLock().unlock();
				return next;
			}// else {
				// System.out.println(this + " nothing to steal at level " +
				// stealPos + " status " + this.stackedJobs);
			// }
		}
		victim.lock.readLock().unlock();
		return null;
	}
}
