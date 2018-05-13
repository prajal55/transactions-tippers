package com.tippers.multithreading;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.tippers.dbutil.ConnectToMySql;
import com.tippers.dbutil.ConnectToPostgres;
import com.tippers.dbutil.Properties;

public class MultiThreadingTxns {
	
	protected static Queue<String> jobs = new LinkedList<String>();

	synchronized public static boolean isJobQueueEmpty() {
		return jobs.isEmpty();
	}
	
	synchronized public static String getJobToExecute() {
		if(!isJobQueueEmpty()) {
			return jobs.poll();
		}
		else return null;
	}

	private static void readInput(Queue<String> jobs) {
		try {
			File f = new File(Properties.INPUT_FILE);
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line = null;
			int queryCount = 0;
			StringBuilder sb = new StringBuilder();

			while ((line = br.readLine()) != null) {

				if(queryCount<Properties.OPERATIONS_PER_TXN) {
					sb.append(line);
					queryCount++;
					continue;
				}
				jobs.add(sb.toString());
				sb.setLength(0);
				queryCount = 0;
			}
			
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	public static void main(String args[]) throws InterruptedException{

		//read file line by line and push each line into a queue
		//TODO push X number of operations from queue to the threads. X operations = 1 transaction
		//TODO figure out how each thread will obtain the X number of operations

		initializeDB();
		readInput(jobs);

		//RejectedExecutionHandler implementation
		RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl();
		//Get the ThreadFactory implementation to use
		ThreadFactory threadFactory = Executors.defaultThreadFactory();
		//creating the ThreadPoolExecutor
		ThreadPoolExecutor executorPool = new ThreadPoolExecutor(Properties.CORE_POOL_SIZE, Properties.MPL_LEVEL, Properties.KEEP_ALIVE_TIME, TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(2), threadFactory, rejectionHandler);

		//start the monitoring thread
		MonitorThreads monitor = new MonitorThreads(executorPool, 3);
		Thread monitorThread = new Thread(monitor);
		monitorThread.start();
		//submit work to the thread pool
		for(int i=1; i<=Properties.MPL_LEVEL; i++){
			executorPool.execute(new WorkerThread());
		}
		
		Thread.sleep(10000);
		//shut down the pool
		executorPool.shutdown();
		//shut down the monitor thread
		Thread.sleep(5000);
		monitor.shutdown();

	}

	private static void initializeDB() {
		Connection c;
		try {
			if(Properties.CONNECT_TO_PSQL) {
				ConnectToPostgres psqlConnection = new ConnectToPostgres();
				c = psqlConnection.getConnectionToDB(Properties.AUTO_COMMIT);
			}
			else {
				//connectToMysql();
				ConnectToMySql mysqlConnection = new ConnectToMySql();
				c = mysqlConnection.getConnectionToDB(Properties.AUTO_COMMIT);
			}
			
			Statement stmt = c.createStatement();
			stmt.executeUpdate(Properties.SET_DB_PARAMS);
			stmt.close();
			c.commit();
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}
