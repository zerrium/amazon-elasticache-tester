/* 
 * 
 * Copyright 2021 Amazon.com, Inc. or its affiliates.  All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 * 
 */
package com.amazonaws.elasticachedemo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.spy.memcached.MemcachedClient;

/**
 * This class demonstrates how to use Amazon ElastiCache for Memcached using the
 * ElastiCache Cluster Client. This client is required to take advantage of Auto
 * Discovery - the ability for client programs to automatically identify all of
 * the nodes in a cache cluster and to initiate and maintain connections to all
 * of these nodes.
 */
public class MemcachedElastiCacheDemo {

	/** The properties object to hold the config. */
	private Properties properties = null;

	/** The Memcached cluster host name. */
	private String memcachedHost = null;

	/** The Memcached cluster port. */
	private int memcachedPort = 0;

	/** The cache-expiry value (in seconds). */
	private int cacheExpiryInSecs = 0;

	/**
	 * The flag that denotes whether cache needs to be flushed or not on shutdown.
	 */
	private boolean cacheFlushOnShutdown = false;

	/** The flag that denotes whether to suppress client logging or not. */
	private boolean clientSuppressLopgging = false;

	/** The client shutdown timeout value (in seconds). */
	private int clientShutdownTimeoutInSecs = 0;

	/** The number of auto-generated key-value entries. */
	private int numberOfAutoGeneratedEntries = 0;

	/** The Memcached (ElastiCache Cluster) client object. */
	private MemcachedClient memcachedClient = null;

	/**
	 * Constructor performing initialization.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public MemcachedElastiCacheDemo() throws IOException {
		initialize();
	}

	/**
	 * Loads the properties from the config file and starts the Memcached client.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void initialize() throws IOException {
		loadProperties();
		startClient();
	}

	/**
	 * Loads properties from the config file.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void loadProperties() throws IOException {
		System.out.println("Reading config file...");
		properties = new Properties();
		FileInputStream fis = new FileInputStream(new File("resources/Redis_config.properties"));
		properties.load(fis);
		memcachedHost = properties.getProperty("MEMCACHED_CLUSTER_ENDPOINT_HOSTNAME");
		memcachedPort = Integer.parseInt(properties.getProperty("MEMCACHED_CLUSTER_ENDPOINT_PORT"));
		cacheExpiryInSecs = Integer.parseInt(properties.getProperty("MEMCACHED_CACHE_EXPIRY_IN_SECS"));
		cacheFlushOnShutdown = Boolean.parseBoolean(properties.getProperty("MEMCACHED_CACHE_FLUSH_ON_SHUTDOWN"));
		clientSuppressLopgging = Boolean.parseBoolean(properties.getProperty("MEMCACHED_CLIENT_SUPPRESS_LOGGING"));
		clientShutdownTimeoutInSecs = Integer
				.parseInt(properties.getProperty("MEMCACHED_CLIENT_SHUTDOWN_TIMEOUT_IN_SECS"));
		numberOfAutoGeneratedEntries = Integer.parseInt(properties.getProperty("NUMBER_OF_AUTO_GENERATED_ENTRIES"));
		fis.close();
		System.out.println("Completed reading config file.");
	}

	/**
	 * Starts the Memcached client.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void startClient() throws IOException {
		System.out.println("Initializing Memcached client...");
		System.setProperty("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.SunLogger");
		if (clientSuppressLopgging) {
			Logger.getLogger("net.spy.memcached").setLevel(Level.OFF);
		} else {
			Logger.getLogger("net.spy.memcached").setLevel(Level.ALL);
		}
		memcachedClient = new MemcachedClient(new InetSocketAddress(memcachedHost, memcachedPort));
		System.out.println("Completed initializing Memcached client.");
	}

	/**
	 * Stops the Memcached client.
	 */
	private void stopClient() {
		System.out
				.println("Shutting down Memcached client (timeout = " + clientShutdownTimeoutInSecs + " second(s))...");
		boolean result = memcachedClient.shutdown(clientShutdownTimeoutInSecs, TimeUnit.SECONDS);
		if (result) {
			System.out.println("Completed shutting down Memcached client.");
		} else {
			System.out.println("Shutdown did not complete before timeout.");
		}
	}

	/**
	 * Upserts cache entries - loops through and calls the upsertCacheEntry method.
	 *
	 * @param keyPrefix   the key prefix
	 * @param valuePrefix the value prefix
	 * @param checkExists the check exists
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void upsertCacheEntries(String keyPrefix, String valuePrefix, boolean checkExists)
			throws InterruptedException, ExecutionException {
		for (int i = 1; i <= numberOfAutoGeneratedEntries; i++) {
			upsertCacheEntry("Name" + i, "Value" + i, false);
		}
	}

	/**
	 * Upsert cache entry - adds if not present; else updates based on the key.
	 *
	 * @param key         the key
	 * @param value       the value
	 * @param checkExists the check exists
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void upsertCacheEntry(String key, String value, boolean checkExists)
			throws InterruptedException, ExecutionException {
		boolean valueExists = false;
		if (checkExists && (getCacheValue(key) != null)) {
			valueExists = true;
		}
		Future<Boolean> result = memcachedClient.set(key, cacheExpiryInSecs, value);
		if (result.get().booleanValue()) {
			if (checkExists) {
				if (valueExists) {
					System.out.println("Updated = {key=" + key + ", value=" + value + "}");
				} else {
					System.out.println("Inserted = {key=" + key + ", value=" + value + "}");
				}
			} else {
				System.out.println("Upserted = {key=" + key + ", value=" + value + "}");
			}
		} else {
			System.out.println("Could not upsert key '" + key + "'");
		}
	}

	/**
	 * Deletes a random cache entry; uses the key-prefix and generates the full key
	 * randomly.
	 *
	 * @param keyPrefix the key prefix
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void deleteRandomCacheEntry(String keyPrefix) throws InterruptedException, ExecutionException {
		deleteCacheEntry(keyPrefix + getRandomInteger(1, numberOfAutoGeneratedEntries));
	}

	/**
	 * Deletes the cache entry for the specified key.
	 *
	 * @param key the key
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void deleteCacheEntry(String key) throws InterruptedException, ExecutionException {
		Future<Boolean> result = memcachedClient.delete(key);
		if (result.get().booleanValue()) {
			System.out.println("Deleted key '" + key + "'");
			System.out.println("Testing delete...");
			getCacheValue(key);
			System.out.println("Completed testing delete.");
		} else {
			System.out.println("Could not delete key '" + key + "'");
		}
	}

	/**
	 * Flushes the cache.
	 *
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void flushCache() throws InterruptedException, ExecutionException {
		long startTime = (new Date()).getTime();
		Future<Boolean> result = memcachedClient.flush();
		long endTime = (new Date()).getTime();
		if (result.get().booleanValue()) {
			System.out.println("Flushed cache in " + (endTime - startTime) + " millisecond(s).");
		} else {
			System.out.println("Could not flush cache.");
		}
	}

	/**
	 * Gets the random cache value; uses the key-prefix and generates the full key
	 * randomly.
	 *
	 * @param keyPrefix the key prefix
	 * @return the random cache value
	 */
	private Object getRandomCacheValue(String keyPrefix) {
		return getCacheValue(keyPrefix + getRandomInteger(1, numberOfAutoGeneratedEntries));
	}

	/**
	 * Gets the specified cache value.
	 *
	 * @param key the key
	 * @return the cache value
	 */
	private Object getCacheValue(String key) {
		long startTime = (new Date()).getTime();
		Object valueObj = memcachedClient.get(key);
		long endTime = (new Date()).getTime();
		if (valueObj != null) {
			System.out.println("Retrieved value='" + valueObj.toString() + "' for key= '" + key + "' in "
					+ (endTime - startTime) + " millisecond(s).");
		} else {
			System.out.println("Key '" + key + "' not found.");
		}
		return valueObj;
	}

	/**
	 * Perform shutdown - flush the cache and stop the Memcached client.
	 *
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	private void shutdown() throws InterruptedException, ExecutionException {
		if (cacheFlushOnShutdown) {
			flushCache();
		}
		stopClient();
	}

	/**
	 * Gets a random integer from a range of integers.
	 *
	 * @param minimum the minimum
	 * @param maximum the maximum
	 * @return the random integer
	 */
	private int getRandomInteger(int minimum, int maximum) {
		return ((int) (Math.random() * (maximum - minimum))) + minimum;
	}

	/**
	 * The main method performs the following,
	 * 1. Reads the config file.
	 * 2. Instantiates the Memcached (ElastiCache Cluster) client.
	 * 3. Upserts the specified number of key-values in the cache.
	 * 4. Prints the value of a random key in the range.
	 * 5. Deletes the key-value entry for a random key in the range.
	 * 6. Flushes the cache (if enabled).
	 * 7. Shuts down the Memcached (ElastiCache Cluster) client.
	 *
	 * @param args the arguments
	 * @throws IOException          Signals that an I/O exception has occurred.
	 * @throws InterruptedException the interrupted exception
	 * @throws ExecutionException   the execution exception
	 */
	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		MemcachedElastiCacheDemo memcachedElastiCacheDemo = new MemcachedElastiCacheDemo();
		memcachedElastiCacheDemo.upsertCacheEntries("Name", "Value", false);
		memcachedElastiCacheDemo.getRandomCacheValue("Name");
		memcachedElastiCacheDemo.deleteRandomCacheEntry("Name");
		memcachedElastiCacheDemo.shutdown();
	}
}