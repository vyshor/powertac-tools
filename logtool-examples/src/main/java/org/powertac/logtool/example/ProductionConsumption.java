/*
 * Copyright (c) 2015 by John E. Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.logtool.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import org.apache.log4j.Logger;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Instant;
import org.powertac.common.TariffTransaction;
import org.powertac.common.msg.TimeslotUpdate;
import org.powertac.common.spring.SpringApplicationContext;
import org.powertac.logtool.LogtoolContext;
import org.powertac.logtool.common.DomainObjectReader;
import org.powertac.logtool.common.NewObjectListener;
import org.powertac.logtool.ifc.Analyzer;

/**
 * Example analysis class.
 * Gathers customer production and consumption data
 * For each timeslot, report includes
 *   timeslot index, day of week, hour, total production, total consumption
 * 
 * @author John Collins
 */
public class ProductionConsumption
extends LogtoolContext
implements Analyzer
{
  static private Logger log = Logger.getLogger(ProductionConsumption.class.getName());

  private DomainObjectReader dor;

  // data collectors for current timeslot
  private int timeslot;
  private double used = 0.0;
  private double produced = 0.0;

  // data output file
  private PrintWriter data = null;
  private String dataFilename = "data.txt";
  private boolean dataInit = false;

  /**
   * Constructor does nothing. Call setup() before reading a file to
   * get this to work.
   */
  public ProductionConsumption ()
  {
    super();
  }
  
  /**
   * Main method just creates an instance and passes command-line args to
   * its inherited cli() method.
   */
  public static void main (String[] args)
  {
    new ProductionConsumption().cli(args);
  }
  
  /**
   * Takes two args, input filename and output filename
   */
  private void cli (String[] args)
  {
    if (args.length != 2) {
      System.out.println("Usage: <analyzer> input-file output-file");
      return;
    }
    dataFilename = args[1];
    super.cli(args[0], this);
  }

  /**
   * Creates data structures, opens output file. It would be nice to dump
   * the broker names at this point, but they are not known until we hit the
   * first timeslotUpdate while reading the file.
   */
  @Override
  public void setup ()
  {
    dor = (DomainObjectReader) SpringApplicationContext.getBean("reader");

    dor.registerNewObjectListener(new TimeslotUpdateHandler(),
                                  TimeslotUpdate.class);
    dor.registerNewObjectListener(new TariffTxHandler(),
                                  TariffTransaction.class);
    try {
      data = new PrintWriter(new File(dataFilename));
    }
    catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    dataInit = false;
  }

  @Override
  public void report ()
  {
    data.close();
  }

  // Called on timeslotUpdate. Note that there are two of these before
  // the first "real" timeslot. Incoming tariffs are published at the end of
  // the second timeslot (the third call to this method), and so customer
  // consumption against non-default broker tariffs first occurs after
  // four calls.
  private void summarizeTimeslot (Instant instant)
  {
    if (!dataInit) {
      // first time through nothing to but print header
      data.println("slot, dow, hour, production, consumption");
      dataInit = true;
      return;
    }

    // print timeslot index and dow
    data.print(String.format("%d, %d, %d, ",
                             timeslot,
                             instant.get(DateTimeFieldType.dayOfWeek()),
                             instant.get(DateTimeFieldType.hourOfDay())));
    // print customer usage, production
    data.println(String.format("%.3f, %.3f", produced, used));
    produced = 0.0;
    used = 0.0;
  }

  // -----------------------------------
  // catch TimeslotUpdate events
  class TimeslotUpdateHandler implements NewObjectListener
  {

    @Override
    public void handleNewObject (Object thing)
    {
      TimeslotUpdate msg = (TimeslotUpdate) thing;
      timeslot = msg.getFirstEnabled() - 1;
      log.info("Timeslot " + timeslot);
      summarizeTimeslot(msg.getPostedTime());
    }
  }

  // -----------------------------------
  // catch TariffTransactions
  class TariffTxHandler implements NewObjectListener
  {
    @Override
    public void handleNewObject (Object thing)
    {
      TariffTransaction tx = (TariffTransaction)thing;

      if (tx.getTxType() == TariffTransaction.Type.CONSUME) {
        used += tx.getKWh() / 1000.0;
      }
      else if (tx.getTxType() == TariffTransaction.Type.PRODUCE) {
        produced += tx.getKWh() / 1000.0;
      }
    } 
  }
}
