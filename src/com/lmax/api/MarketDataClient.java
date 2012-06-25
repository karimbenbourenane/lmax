package com.lmax.api;

import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import com.lmax.api.account.LoginCallback;
import com.lmax.api.account.LoginRequest;
import com.lmax.api.account.LoginRequest.ProductType;
import com.lmax.api.orderbook.Instrument;
import com.lmax.api.orderbook.OrderBookEvent;
import com.lmax.api.orderbook.OrderBookEventListener;
import com.lmax.api.orderbook.OrderBookSubscriptionRequest;
import com.lmax.api.orderbook.PricePoint;
import com.lmax.api.orderbook.SearchInstrumentCallback;
import com.lmax.api.orderbook.SearchInstrumentRequest;

public class MarketDataClient implements LoginCallback, OrderBookEventListener,
    StreamFailureListener {
  private final Map<Long, InstrumentInfo> instrumentInfoById = new HashMap<Long, InstrumentInfo>();
  private int failureCount = 5;

  private Session session;

  public MarketDataClient() {
  }

  @Override
  public void notifyStreamFailure(Exception e) {
    System.out.println("Error occured on the stream");
    e.printStackTrace(System.out);

    if ("UNAUTHENTICATED".equals(e.getMessage())
        || e instanceof FileNotFoundException) {
      session.stop();
    }

    if (--failureCount == -1) {
      session.stop();
    }
  }

  @Override
  public void notify(OrderBookEvent orderBookEvent) {
    long instrumentId = orderBookEvent.getInstrumentId();
    FixedPointNumber bestBid = getBestPrice(orderBookEvent.getBidPrices());
    FixedPointNumber bestAsk = getBestPrice(orderBookEvent.getAskPrices());

    if (instrumentInfoById.containsKey(instrumentId)) {
      instrumentInfoById.get(instrumentId).update(bestBid, bestAsk);
    }
  }

  private FixedPointNumber getBestPrice(List<PricePoint> prices) {
    return prices.size() != 0 ? prices.get(0).getPrice()
        : FixedPointNumber.ZERO;
  }

  @Override
  public void onLoginSuccess(Session session) {
    System.out.println("My accountId is: "
        + session.getAccountDetails().getAccountId());

    this.session = session;
    this.session.registerOrderBookEventListener(this);
    this.session.registerStreamFailureListener(this);

    loadAllInstruments();

    for (long instrumentId : instrumentInfoById.keySet()) {
      subscribeToInstrument(instrumentId);
    }

    session.start();
  }

  private void loadAllInstruments() {
    final long[] offset = { 0 };
    final boolean[] hasMore = { true };

    while (hasMore[0]) {
      session.searchInstruments(new SearchInstrumentRequest("", offset[0]),
          new SearchInstrumentCallback() {
            @Override
            public void onSuccess(List<Instrument> instruments,
                boolean hasMoreResults) {
              hasMore[0] = hasMoreResults;

              for (Instrument instrument : instruments) {
                System.out.println("Instrument: " + instrument.getId() + ", "
                    + instrument.getName());

                InstrumentInfo instrumentInfo = registerMBean(
                    instrument.getId(), instrument.getName());
                if (null != instrumentInfo) {
                  instrumentInfoById.put(instrumentInfo.getInstrumentId(),
                      instrumentInfo);
                }
                offset[0] = instrument.getId();
              }
            }

            @Override
            public void onFailure(FailureResponse failureResponse) {
              hasMore[0] = false;
              throw new RuntimeException("Failed: " + failureResponse);
            }
          });
    }
  }

  private void subscribeToInstrument(long instrumentId) {
    System.out.printf("Subscribing to: %d%n", instrumentId);

    this.session.subscribe(new OrderBookSubscriptionRequest(instrumentId),
        new Callback() {
          public void onSuccess() {
          }

          @Override
          public void onFailure(final FailureResponse failureResponse) {
            throw new RuntimeException("Failed: " + failureResponse);
          }
        });
  }

  private static InstrumentInfo registerMBean(long instrumentId,
      String instrumentName) {
    System.out.printf("Registering mbean for: %d%n", instrumentId);

    InstrumentInfo instrumentInfo = new InstrumentInfo(instrumentId,
        instrumentName);
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

    try {
      ObjectName name = new ObjectName(
          "com.lmax.instruments:type=instrument,id=" + instrumentId);
      mbs.registerMBean(new StandardMBean(instrumentInfo,
          InstrumentInfoMBean.class), name);
      return instrumentInfo;
    } catch (Exception e) {
      System.err.println("Unable to register instrument: " + instrumentName);
      return null;
    }
  }

  @Override
  public void onLoginFailure(FailureResponse failureResponse) {
    System.out.println("Login Failed: " + failureResponse);
  }

  private static class InstrumentInfo implements InstrumentInfoMBean {
    private final long instrumentId;
    private long lastUpdate = 0;
    private FixedPointNumber bestBid = FixedPointNumber.ZERO;
    private FixedPointNumber bestAsk = FixedPointNumber.ZERO;
    private final String instrumentName;

    public InstrumentInfo(long instrumentId, String instrumentName) {
      this.instrumentId = instrumentId;
      this.instrumentName = instrumentName;
    }

    @Override
    public String getName() {
      return instrumentName;
    }

    @Override
    public long getInstrumentId() {
      return instrumentId;
    }

    @Override
    public Date getLastUpdate() {
      return new Date(lastUpdate);
    }

    @Override
    public String getBestBid() {
      return bestBid.toString();
    }

    @Override
    public String getBestAsk() {
      return bestAsk.toString();
    }

    public void update(FixedPointNumber bid, FixedPointNumber ask) {
      bestBid = bid;
      bestAsk = ask;

      lastUpdate = System.currentTimeMillis();
    }
  }

  public static void main(String[] args) throws InterruptedException {
    if (args.length < 4) {
      System.out
          .println("Usage "
              + MarketDataClient.class.getName()
              + " <url> <username> <password> [CFD_DEMO|CFD_LIVE] [instrumentIds...]");
      System.exit(-1);
    }

    String url = args[0];
    String username = args[1];
    String password = args[2];
    ProductType productType = ProductType.valueOf(args[3].toUpperCase());

    do {
      System.out.printf("Attempting to login to: %s as %s%n", url, username);

      LmaxApi lmaxApi = new LmaxApi(url);
      MarketDataClient loginClient = new MarketDataClient();

      lmaxApi.login(new LoginRequest(username, password, productType),
          loginClient);

      System.out.println("Logged out, pausing for 10s before retrying");
      Thread.sleep(10000);
    } while (true);
  }
}
