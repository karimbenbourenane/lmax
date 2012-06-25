package com.lmax.api;

import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.orderbook.OrderBookEvent;
import com.lmax.api.orderbook.OrderBookEventListener;
import com.lmax.api.orderbook.OrderBookSubscriptionRequest;

/**
 * Demonstrates how to subscribe to live prices for two instruments.
 */
public class MarketDataSubscriber implements LoginCallback,
    OrderBookEventListener {
  private static final long INSTRUMENT_ID = 4001;
  private static final long INSTRUMENT_ID_2 = 100613;

  @Override
  public void onLoginSuccess(final Session session) {
    session.registerOrderBookEventListener(this);
    subscribeToInstrument(session, INSTRUMENT_ID);
    subscribeToInstrument(session, INSTRUMENT_ID_2);

    session.start();
  }

  private void subscribeToInstrument(final Session session,
      final long instrumentId) {
    session.subscribe(new OrderBookSubscriptionRequest(instrumentId),
        new Callback() {
          public void onSuccess() {
            System.out.printf("Subscribed to instrument %d.%n", instrumentId);
          }

          public void onFailure(final FailureResponse failureResponse) {
            System.err.printf("Failed to subscribe to instrument %d: %s%n",
                instrumentId, failureResponse);
          }
        });
  }

  @Override
  public void onLoginFailure(final FailureResponse failureResponse) {
    throw new RuntimeException("Unable to login: "
        + failureResponse.getDescription(), failureResponse.getException());
  }

  @Override
  public void notify(final OrderBookEvent orderBookEvent) {
    System.out.println(orderBookEvent);
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage " + MarketDataSubscriber.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    LoginRequest.ProductType productType = LoginRequest.ProductType
        .valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    MarketDataSubscriber marketDataRequester = new MarketDataSubscriber();

    lmaxApi.login(new LoginRequest(username, password, productType),
        marketDataRequester);
  }
}