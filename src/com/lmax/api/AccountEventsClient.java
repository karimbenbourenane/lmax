package com.lmax.api;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.lmax.api.account.AccountStateEvent;
import com.lmax.api.account.AccountStateEventListener;
import com.lmax.api.account.AccountSubscriptionRequest;
import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.account.LoginRequest.ProductType;
import com.lmax.api.heartbeat.HeartbeatEventListener;
import com.lmax.api.order.Execution;
import com.lmax.api.order.ExecutionEventListener;
import com.lmax.api.order.Order;
import com.lmax.api.order.OrderEventListener;
import com.lmax.api.position.PositionEvent;
import com.lmax.api.position.PositionEventListener;
import com.lmax.api.reject.InstructionRejectedEvent;
import com.lmax.api.reject.InstructionRejectedEventListener;

public class AccountEventsClient implements LoginCallback,
    AccountStateEventListener, ExecutionEventListener,
    InstructionRejectedEventListener, OrderEventListener,
    PositionEventListener, SessionDisconnectedListener, HeartbeatEventListener,
    StreamFailureListener {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
      "yy-MM-dd HH:mm:ss.SSS");
  private final URL longPollKeyRequestUrl;

  @Override
  public void onLoginSuccess(final Session session) {
    System.out.println("My accountId is: "
        + session.getAccountDetails().getAccountId());

    startSessionKeepAliveThread(session);

    session.registerAccountStateEventListener(this);
    session.registerExecutionEventListener(this);
    session.registerInstructionRejectedEventListener(this);
    session.registerOrderEventListener(this);
    session.registerPositionEventListener(this);
    session.registerSessionDisconnectedListener(this);
    session.registerHeartbeatListener(this);
    session.registerStreamFailureListener(this);

    session.subscribe(new AccountSubscriptionRequest(), new Callback() {
      @Override
      public void onSuccess() {
        session.start();
      }

      @Override
      public void onFailure(final FailureResponse failureResponse) {
        throw new RuntimeException("Failed");
      }
    });
  }

  @Override
  public void onLoginFailure(FailureResponse failureResponse) {
    System.out.println("Login Failed: " + failureResponse);
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage " + AccountEventsClient.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    ProductType productType = ProductType.valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    AccountEventsClient accountEventsClient = new AccountEventsClient(url);

    lmaxApi.login(new LoginRequest(username, password, productType),
        accountEventsClient);
  }

  @Override
  public void notify(final AccountStateEvent accountStateEvent) {
    logEvent(accountStateEvent);
  }

  @Override
  public void notify(final Execution execution) {
    logEvent(execution);
  }

  @Override
  public void notify(final InstructionRejectedEvent instructionRejected) {
    logEvent(instructionRejected);
  }

  @Override
  public void notify(final Order order) {
    logEvent(order);
  }

  @Override
  public void notify(final PositionEvent positionEvent) {
    logEvent(positionEvent);
  }

  @Override
  public void notifySessionDisconnected() {
    logEvent("Session disconnected");
  }

  @Override
  public void notify(final long accountId, final String token) {
    logEvent("Heartbeat: " + token);
  }

  @Override
  public void notifyStreamFailure(final Exception exception) {
    logEvent(exception);
  }

  private void logEvent(final Object event) {
    System.out.println(DATE_FORMAT.format(new Date()) + "  " + event);
  }

  private void startSessionKeepAliveThread(final Session session) {
    new Thread() {
      @Override
      public void run() {
        // noinspection InfiniteLoopStatement
        while (true) {
          // Ordinarily, we would use a heartbeat, but a heartbeat would send
          // data to all heartbeat subscribers
          // (interfering with their streams).
          // The long poll key request won't send anything down the async
          // channel.
          // The call will fail because I'm not accepting XML or JSON, but it's
          // fine: it still refreshes the session.
          session.openUrl(longPollKeyRequestUrl, new UrlCallback() {
            @Override
            public void onSuccess(final URL url, final InputStream inputStream) {
              // do nothing
            }

            @Override
            public void onFailure(final FailureResponse failureResponse) {
              // do nothing
            }
          });

          try {
            Thread.sleep(30 * 1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }.start();
  }

  public AccountEventsClient(final String urlBase) {
    try {
      this.longPollKeyRequestUrl = new URL(urlBase + "/secure/longPollKey");
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
