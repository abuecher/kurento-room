/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.kurento.room.test.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.kurento.room.test.RoomTestBase;
import org.openqa.selenium.WebDriver;

import com.google.common.base.Function;

public class UnsubscribeFromMediaRoomDemoTest extends RoomTestBase {

	private static final int PLAY_TIME = 5; // seconds

	private static final int NUM_USERS = 3;

	@BeforeClass
	public static void setupBeforeClass() {
		APP_URL = "http://127.0.0.1:8080";
	}

	@Test
	public void test() throws Exception {

		final boolean[] activeUsers = new boolean[NUM_USERS];
		final Object browsersLock = new Object();

		final CountDownLatch joinCdl = new CountDownLatch(NUM_USERS);
		final CountDownLatch publishCdl =
				new CountDownLatch(NUM_USERS * NUM_USERS);
		final CountDownLatch unsubscribeCdl = new CountDownLatch(NUM_USERS);
		final CountDownLatch verifyCdl = new CountDownLatch(NUM_USERS);
		final CountDownLatch leaveCdl = new CountDownLatch(NUM_USERS);

		// parallelUsers(NUM_USERS, (numUser, browser) -> {

		final int unsubscribeFrom = random.nextInt(NUM_USERS);
		final String clickableVideoTagId =
				"video-user" + unsubscribeFrom + "_webcam";

		parallelUsers(NUM_USERS, new UserLifecycle() {
			@Override
			public void run(int numUser, final WebDriver browser)
					throws InterruptedException, ExecutionException {

				final String userName = "user" + numUser;

				synchronized (browsersLock) {
					joinToRoom(browser, userName, roomName);
					log.info("User '{}' joined to room '{}'", userName,
							roomName);
					activeUsers[numUser] = true;
					verify(browsers, activeUsers);

					joinCdl.countDown();
				}
				joinCdl.await(PLAY_TIME * 5000L, TimeUnit.MILLISECONDS);

				final long start = System.currentTimeMillis();

				parallelTask(NUM_USERS, new Function<Integer, Void>() {
					@Override
					public Void apply(Integer num) {
						String videoUserName = "user" + num;
						synchronized (browsersLock) {
							waitForStream(browser, "native-video-user" + num
									+ "_webcam");
						}
						long duration = System.currentTimeMillis() - start;
						log.info(
								"Video received in browser of user {} for user '{}' in {} millis",
								userName, videoUserName, duration);
						publishCdl.countDown();
						return null;
					}
				});

				publishCdl.await(PLAY_TIME * 5000L, TimeUnit.MILLISECONDS);

				if (numUser != unsubscribeFrom) {
					log.info(
							"User '{}' unsubscribing from 'user{}' (vTag={}) in room '{}'",
							userName, unsubscribeFrom, clickableVideoTagId,
							roomName);
					synchronized (browsersLock) {
						unsubscribe(browser, clickableVideoTagId);
					}
					log.info(
							"User '{}' unsubscribed from 'user{}' in room '{}'",
							userName, unsubscribeFrom, roomName);
				} else {
					activeUsers[numUser] = false;
				}
				unsubscribeCdl.countDown();
				unsubscribeCdl.await(PLAY_TIME * 5000L, TimeUnit.MILLISECONDS);

				if (numUser != unsubscribeFrom) {
					synchronized (browsersLock) {
						verify(browsers, activeUsers);
					}
					log.info(
							"{} - Verified that I've unsubscribed from 'user{}' media in room '{}'",
							userName, unsubscribeFrom, roomName);
				}
				verifyCdl.countDown();
				verifyCdl.await(PLAY_TIME * 5000L, TimeUnit.MILLISECONDS);

				synchronized (browsersLock) {
					log.info("User '{}' exiting from room '{}'", userName,
							roomName);
					exitFromRoom(browser);
					log.info("User '{}' exited from room '{}'", userName,
							roomName);
					activeUsers[numUser] = false;
					verify(browsers, activeUsers);

					leaveCdl.countDown();
				}
				leaveCdl.await(PLAY_TIME * 5000L, TimeUnit.MILLISECONDS);

				log.info("User '{}' closed browser", userName);
			}
		});
	}
}
