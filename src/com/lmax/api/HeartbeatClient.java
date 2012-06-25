package com.lmax.api;

import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.account.LoginRequest.ProductType;
import com.lmax.api.heartbeat.HeartbeatCallback;
import com.lmax.api.heartbeat.HeartbeatEventListener;
import com.lmax.api.heartbeat.HeartbeatRequest;
import com.lmax.api.heartbeat.HeartbeatSubscriptionRequest;

public class HeartbeatClient implements LoginCallback, HeartbeatEventListener,
    Runnable {
  private Session session;

  public HeartbeatClient() {
  }

  @Override
  public void notify(long accountId, String token) {
    System.out.printf("Received heartbeat: %d, %s%n", accountId, token);
  }

  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(2000);

        requestHeartbeat();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onLoginSuccess(Session session) {
    System.out.println("My accountId is: "
        + session.getAccountDetails().getAccountId());

    this.session = session;
    this.session.registerHeartbeatListener(this);

    session.subscribe(new HeartbeatSubscriptionRequest(), new Callback() {
      public void onSuccess() {
      }

      @Override
      public void onFailure(final FailureResponse failureResponse) {
        throw new RuntimeException("Failed");
      }
    });

    new Thread(this).start();

    session.start();
  }

  private void requestHeartbeat() {
    this.session.requestHeartbeat(new HeartbeatRequest("token"),
        new HeartbeatCallback() {
          @Override
          public void onSuccess(String token) {
            System.out.println("Requested heartbeat: " + token);
          }

          @Override
          public void onFailure(FailureResponse failureResponse) {
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
      System.out.println("Usage " + HeartbeatClient.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    ProductType productType = ProductType.valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    HeartbeatClient loginClient = new HeartbeatClient();

    lmaxApi.login(new LoginRequest(username, password, productType),
        loginClient);
  }
}
