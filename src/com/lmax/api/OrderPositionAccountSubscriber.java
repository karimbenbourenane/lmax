package com.lmax.api;

import com.lmax.api.account.AccountStateEvent;
import com.lmax.api.account.AccountStateEventListener;
import com.lmax.api.account.AccountSubscriptionRequest;
import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.order.Order;
import com.lmax.api.order.OrderEventListener;
import com.lmax.api.order.OrderSubscriptionRequest;
import com.lmax.api.position.PositionEvent;
import com.lmax.api.position.PositionEventListener;
import com.lmax.api.position.PositionSubscriptionRequest;

/**
 * Demonstrates how to subscribe to order updates, position updates and account
 * updates.
 * 
 * Run this program and use the Web GUI to place orders. You will see the
 * order/position/account updates.
 */
public class OrderPositionAccountSubscriber implements LoginCallback,
    OrderEventListener, PositionEventListener, AccountStateEventListener {
  @Override
  public void onLoginSuccess(final Session session) {
    session.registerOrderEventListener(this);
    subscribe(session, new OrderSubscriptionRequest(), "Orders");

    session.registerPositionEventListener(this);
    subscribe(session, new PositionSubscriptionRequest(), "Positions");

    session.registerAccountStateEventListener(this);
    subscribe(session, new AccountSubscriptionRequest(), "Account Updates");

    session.start();
  }

  @Override
  public void notify(final Order order) {
    System.out.println("Order update: " + order);
  }

  @Override
  public void notify(final PositionEvent positionEvent) {
    System.out.println("Position update: " + positionEvent);
  }

  @Override
  public void notify(final AccountStateEvent accountStateEvent) {
    System.out.println("Account state update: " + accountStateEvent);
  }

  @Override
  public void onLoginFailure(final FailureResponse failureResponse) {
    throw new RuntimeException("Unable to login: "
        + failureResponse.getDescription(), failureResponse.getException());
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage "
          + OrderPositionAccountSubscriber.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    LoginRequest.ProductType productType = LoginRequest.ProductType
        .valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    OrderPositionAccountSubscriber marketDataRequester = new OrderPositionAccountSubscriber();

    lmaxApi.login(new LoginRequest(username, password, productType),
        marketDataRequester);
  }

  private void subscribe(final Session session,
      final SubscriptionRequest request, final String subscriptionDescription) {
    session.subscribe(request, new Callback() {
      public void onSuccess() {
        System.out.println("Subscribed to " + subscriptionDescription);
      }

      public void onFailure(final FailureResponse failureResponse) {
        System.err.printf("Failed to subscribe to " + subscriptionDescription
            + ": %s%n", failureResponse);
      }
    });
  }
}