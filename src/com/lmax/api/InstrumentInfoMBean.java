package com.lmax.api;

import java.util.Date;

public interface InstrumentInfoMBean {
  long getInstrumentId();

  Date getLastUpdate();

  String getBestBid();

  String getBestAsk();

  String getName();
}
