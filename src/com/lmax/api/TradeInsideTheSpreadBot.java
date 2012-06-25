package com.lmax.api;

import java.util.List;

import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.account.LoginRequest.ProductType;
import com.lmax.api.order.CancelOrderRequest;
import com.lmax.api.order.LimitOrderSpecification;
import com.lmax.api.order.Order;
import com.lmax.api.order.OrderCallback;
import com.lmax.api.order.OrderEventListener;
import com.lmax.api.order.OrderSubscriptionRequest;
import com.lmax.api.orderbook.OrderBookEvent;
import com.lmax.api.orderbook.OrderBookEventListener;
import com.lmax.api.orderbook.OrderBookSubscriptionRequest;
import com.lmax.api.orderbook.PricePoint;
import com.lmax.api.reject.InstructionRejectedEvent;
import com.lmax.api.reject.InstructionRejectedEventListener;

public class TradeInsideTheSpreadBot implements LoginCallback,
    OrderBookEventListener, OrderEventListener,
    InstructionRejectedEventListener {
  enum OrderState {
    NONE, PENDING, WORKING
  }

  private Session session;
  private final long instrumentId;
  private final FixedPointNumber tickSize;

  private final OrderTracker buyOrderTracker = new OrderTracker();
  private final OrderTracker sellOrderTracker = new OrderTracker();

  public TradeInsideTheSpreadBot(long instrumentId, FixedPointNumber tickSize) {
    this.instrumentId = instrumentId;
    this.tickSize = tickSize;
  }

  @Override
  public void notify(OrderBookEvent orderBookEvent) {
    System.out.println(orderBookEvent);

    // React to price updates from the exchange.
    handleBidPrice(orderBookEvent.getBidPrices());
    handleAskPrice(orderBookEvent.getAskPrices());
  }

  @Override
  public void notify(InstructionRejectedEvent instructionRejected) {
    System.err.println(instructionRejected);

    final long instructionId = instructionRejected.getInstructionId();
    if (buyOrderTracker.getCancelInstructionId() == instructionId) {
      buyOrderTracker.setOrderState(OrderState.NONE);
    } else if (sellOrderTracker.getCancelInstructionId() == instructionId) {
      sellOrderTracker.setOrderState(OrderState.NONE);
    }
  }

  @Override
  public void notify(Order order) {
    OrderState stateForOrder = getStateForOrder(order);
    System.out.printf("State for order: %d, state: %s%n",
        order.getInstructionId(), stateForOrder);

    if (order.getInstructionId() == buyOrderTracker.getInstructionId()) {
      buyOrderTracker.setOrderState(stateForOrder);
    } else if (order.getInstructionId() == sellOrderTracker.getInstructionId()) {
      sellOrderTracker.setOrderState(stateForOrder);
    }
  }

  private OrderState getStateForOrder(Order order) {
    if (order.getCancelledQuantity() == FixedPointNumber.ZERO
        && order.getFilledQuantity() == FixedPointNumber.ZERO) {
      return OrderState.WORKING;
    }

    return OrderState.NONE;
  }

  private void handleAskPrice(List<PricePoint> askPrices) {
    handlePriceChange(askPrices, sellOrderTracker,
        FixedPointNumber.ONE.negate(), tickSize.negate());
  }

  private void handleBidPrice(List<PricePoint> bidPrices) {
    handlePriceChange(bidPrices, buyOrderTracker, FixedPointNumber.ONE,
        tickSize);
  }

  private void handlePriceChange(List<PricePoint> prices,
      final OrderTracker orderTracker, final FixedPointNumber quantity,
      final FixedPointNumber priceDelta) {
    final FixedPointNumber currentPrice = orderTracker.getPrice();
    PricePoint bestPrice = prices.size() != 0 ? prices.get(0) : null;

    // Make sure we have a best bid price, and it's not the same as the order we
    // just placed
    // and place similar to the ask price change.
    if (bestPrice != null
        && (currentPrice == null || currentPrice == bestPrice.getPrice())) {
      switch (orderTracker.getOrderState()) {
      // Place an order inside the spread if there isn't one currently in the
      // market
        case NONE:
          orderTracker.setPrice(FixedPointNumber.valueOf(bestPrice.getPrice()
              .longValue() + priceDelta.longValue()));

          LimitOrderSpecification order = new LimitOrderSpecification(
              instrumentId, orderTracker.getPrice(), quantity,
              TimeInForce.GOOD_FOR_DAY);

          session.placeLimitOrder(order, new DefaultOrderCallback() {
            public void onSuccess(long instructionId) {
              System.out.println("Placed Order: " + instructionId);
              orderTracker.setOrderState(OrderState.PENDING);
              orderTracker.setInstructionId(instructionId);
            }
          });
          break;

        // Cancel a working order on a price change.
        case WORKING:
          cancelOrder(orderTracker);

        default:
          // No-op
      }
    }
  }

  private void cancelOrder(final OrderTracker orderTracker) {
    final long instructionId = orderTracker.getInstructionId();

    if (instructionId != -1) {
      CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(
          instrumentId, instructionId);
      session.cancelOrder(cancelOrderRequest, new DefaultOrderCallback() {
        public void onSuccess(long cancelInstructionId) {
          System.out.println("Cancled Order: " + cancelInstructionId);
          orderTracker.setCancelInstructionId(cancelInstructionId);
        }
      });
    }
  }

  @Override
  public void onLoginSuccess(Session session) {
    System.out.println("My accountId is: "
        + session.getAccountDetails().getAccountId());

    // Hold onto the session for later use.
    this.session = session;

    // Add a listener for order book events.
    session.registerOrderBookEventListener(this);
    session.registerOrderEventListener(this);
    session.registerInstructionRejectedEventListener(this);

    // Subscribe to my order events.
    session.subscribe(new OrderSubscriptionRequest(),
        new DefaultSubscriptionCallback());
    // Subscribe to the order book that I'm interested in.
    session.subscribe(new OrderBookSubscriptionRequest(instrumentId),
        new DefaultSubscriptionCallback());

    // Start the event processing loop, this method will block until the session
    // is stopped.
    session.start();
  }

  @Override
  public void onLoginFailure(FailureResponse failureResponse) {
    System.out.println("Login Failed: " + failureResponse);
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage " + TradeInsideTheSpreadBot.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    ProductType productType = ProductType.valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    TradeInsideTheSpreadBot loginClient = new TradeInsideTheSpreadBot(4012,
        FixedPointNumber.valueOf("0.00001"));

    // Login to LMAX!
    lmaxApi.login(new LoginRequest(username, password, productType),
        loginClient);
  }

  private static class OrderTracker {
    private long instructionId = -1;
    private long cancelInstructionId = -1;
    private OrderState orderState = OrderState.NONE;
    private FixedPointNumber price;

    public long getInstructionId() {
      return instructionId;
    }

    public void setInstructionId(long instructionId) {
      this.instructionId = instructionId;
    }

    public long getCancelInstructionId() {
      return cancelInstructionId;
    }

    public void setCancelInstructionId(long cancelInstructionId) {
      this.cancelInstructionId = cancelInstructionId;
    }

    public OrderState getOrderState() {
      return orderState;
    }

    public void setOrderState(OrderState orderState) {
      this.orderState = orderState;
    }

    public FixedPointNumber getPrice() {
      return price;
    }

    public void setPrice(FixedPointNumber price) {
      this.price = price;
    }
  }

  private abstract static class DefaultOrderCallback implements OrderCallback {
    @Override
    public void onFailure(FailureResponse failureResponse) {
      System.err.println("Failed to place order: " + failureResponse);
    }
  }

  private final class DefaultSubscriptionCallback implements Callback {
    public void onSuccess() {
    }

    @Override
    public void onFailure(final FailureResponse failureResponse) {
      throw new RuntimeException("Failed");
    }
  }
}
