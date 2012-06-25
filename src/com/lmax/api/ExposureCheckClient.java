package com.lmax.api;

import java.io.File;

import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.account.LoginRequest.ProductType;
import com.lmax.api.io.RollingFileWriter;
import com.lmax.api.order.Execution;
import com.lmax.api.order.ExecutionEventListener;
import com.lmax.api.order.MarketOrderSpecification;
import com.lmax.api.order.Order;
import com.lmax.api.order.OrderCallback;
import com.lmax.api.order.OrderEventListener;
import com.lmax.api.order.OrderSubscriptionRequest;
import com.lmax.api.orderbook.OrderBookEvent;
import com.lmax.api.orderbook.OrderBookEventListener;
import com.lmax.api.orderbook.OrderBookSubscriptionRequest;
import com.lmax.api.profile.Timer;
import com.lmax.api.reject.InstructionRejectedEvent;
import com.lmax.api.reject.InstructionRejectedEventListener;

public class ExposureCheckClient implements LoginCallback,
    OrderBookEventListener, OrderEventListener,
    InstructionRejectedEventListener, ExecutionEventListener {
  private final class DefaultCallback implements Callback {
    public void onSuccess() {
    }

    @Override
    public void onFailure(final FailureResponse failureResponse) {
      throw new RuntimeException("Failed");
    }
  }

  private Session session;
  private final long instrumentId;
  private FixedPointNumber side = FixedPointNumber.ZERO;
  private long orderCount = 0;
  private long executionCount = 0;
  private long rejectionCount = 0;

  public ExposureCheckClient(long instrumentId) {
    this.instrumentId = instrumentId;
  }

  @Override
  public void notify(OrderBookEvent orderBookEvent) {
    if (side.equals(FixedPointNumber.ZERO)) {
      side = FixedPointNumber.ONE;
      placeOrder(side);
    }
  }

  @Override
  public void notify(InstructionRejectedEvent instructionRejected) {
    rejectionCount++;
    placeOrder(side);

    System.out.println(instructionRejected.getReason());
  }

  @Override
  public void notify(Execution execution) {
  }

  @Override
  public void notify(Order order) {
    executionCount++;
    side = side.negate();
    placeOrder(side);
  }

  private void placeOrder(FixedPointNumber side) {
    if (orderCount > 2 && (orderCount % 50 == 0 || orderCount % 50 == 1)) {
      session.placeMarketOrder(new MarketOrderSpecification(instrumentId,
          new FixedPointNumber(500000000), TimeInForce.IMMEDIATE_OR_CANCEL),
          placeOrderCallback);
    } else {
      session.placeMarketOrder(new MarketOrderSpecification(instrumentId, side,
          TimeInForce.IMMEDIATE_OR_CANCEL), placeOrderCallback);
    }
  }

  private final OrderCallback placeOrderCallback = new OrderCallback() {
    @Override
    public void onSuccess(long instructionId) {
      if (orderCount % 10 == 0) {
        System.out.printf(
            "Orders: %d, Executions: %d, Rejections: %d, Net: %d%n",
            orderCount, executionCount, rejectionCount, orderCount
                - (executionCount + rejectionCount));
      }

      orderCount++;
    }

    @Override
    public void onFailure(FailureResponse failureResponse) {
      System.out.println(failureResponse);
    }
  };

  @Override
  public void onLoginSuccess(Session session) {
    System.out.println("My accountId is: "
        + session.getAccountDetails().getAccountId());

    this.session = session;
    this.session.registerOrderBookEventListener(this);
    this.session.registerOrderEventListener(Timer.forOrderEvents(this));
    this.session.registerInstructionRejectedEventListener(Timer
        .forInstructionRejectedEvents(this));
    this.session.registerExecutionEventListener(Timer.forExecutionEvents(this));
    this.session.setEventStreamDebug(new RollingFileWriter(
        new File("/tmp/mike"), "event-stream-%s.log", 8192));

    session.subscribe(new OrderBookSubscriptionRequest(instrumentId),
        new DefaultCallback());
    session.subscribe(new OrderSubscriptionRequest(), new DefaultCallback());

    session.start();
  }

  @Override
  public void onLoginFailure(FailureResponse failureResponse) {
    System.out.println("Login Failed: " + failureResponse);
  }

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage " + ExposureCheckClient.class.getName()
          + " <url> <username> <password> [CFD_DEMO|CFD_LIVE]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    ProductType productType = ProductType.valueOf(args[3].toUpperCase());

    LmaxApi lmaxApi = new LmaxApi(url);
    ExposureCheckClient loginClient = new ExposureCheckClient(4001);

    lmaxApi.login(new LoginRequest(username, password, productType),
        loginClient);
  }
}
