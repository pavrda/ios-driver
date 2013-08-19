/*
 * Copyright 2012 ios-driver committers.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License
 *  is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing permissions and limitations under
 *  the License.
 */

package org.uiautomation.ios.server.instruments.communication;

import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.BeanToJsonConverter;
import org.openqa.selenium.remote.Response;
import org.uiautomation.ios.server.command.UIAScriptRequest;
import org.uiautomation.ios.server.command.UIAScriptResponse;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.uiautomation.ios.IOSCapabilities.COMMAND_TIMEOUT_MILLIS;

/**
 * base class for communication with an Instruments process. Assumes asyncronous communication.
 */
public abstract class BaseCommunicationChannel implements CommunicationChannel {


  private final BlockingQueue<UIAScriptResponse> responseQueue = new ArrayBlockingQueue<>(1);

  private final Lock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private volatile boolean ready = false;
  private final String sessionId;

  public BaseCommunicationChannel(String sessionId) {
    this.sessionId = sessionId;
  }

  protected String getSessionId() {
    return sessionId;
  }

  @Override
  public boolean waitForUIScriptToBeStarted() throws InterruptedException {
    try {
      lock.lock();
      if (ready) {
        return true;
      }
      return condition.await(60, TimeUnit.SECONDS);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void registerUIAScript() {
    try {
      lock.lock();
      ready = true;
      condition.signal();
    } finally {
      lock.unlock();
    }
  }

  protected void handleLastCommand(UIAScriptRequest request) {
    // Stop is a fire and forget response. It will kill the instruments script,
    // so the script cannot
    // send a response.
    if ("stop".equals(request.getScript())) {
      Response response = new Response();
      response.setSessionId(getSessionId());
      response.setStatus(0);
      response.setValue("ok");
      BeanToJsonConverter converter = new BeanToJsonConverter();
      String json = converter.convert(response);
      UIAScriptResponse r = new UIAScriptResponse(json);
      setNextResponse(r);
    }

  }

  public void setNextResponse(UIAScriptResponse r) {
    try {
      responseQueue.add(r);

    } catch (IllegalStateException e) {
      try {
        System.out.println("ALREADY PRESENT:"+responseQueue.take().getRaw());
        System.out.println("TRY TO ADD:"+r.getRaw());
      } catch (InterruptedException e1) {
        e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      e.printStackTrace();
    }
  }


  protected UIAScriptResponse waitForResponse() {
    try {
      UIAScriptResponse res = responseQueue.poll(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      return res;
    } catch (InterruptedException e) {
      throw new WebDriverException("timeout getting the response", e);
    }
  }

}
