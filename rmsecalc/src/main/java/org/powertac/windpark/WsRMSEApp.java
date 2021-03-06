package org.powertac.windpark;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.thoughtworks.xstream.XStream;

public class WsRMSEApp {
	
	private static final String wsDataPath = "/tmp/wsdata";
	private static final String wsRmsePath = "/tmp/wsrmse";
	private static final String rmseFileName = "WindSpeedRMSE.xml";
	
	private WsData.WeatherForecasts windSpeedForecasts = new WsData.WeatherForecasts();
	private WsData.WeatherReports   windSpeedObservations = new WsData.WeatherReports();
	
	private Map<Integer, Float> mapLeadTimeRMSE = new HashMap<Integer, Float>();
	private Map<Integer, Set<Float>> mapLtErrorSet = new HashMap<Integer, Set<Float>>();
	private WindSpeedRMSE wspRmse = null;
	
	private static File[] getDataFiles() {
		File dataFolder = new File(wsDataPath);
		return dataFolder.listFiles();
	}

	private static XStream getConfiguredXStream() {
		XStream xstream = new XStream();
		// configure XStream Object
	    xstream.alias("data", WsData.class);
	    xstream.alias("weatherReports", WsData.WeatherReports.class);
	    xstream.alias("weatherForecasts", WsData.WeatherForecasts.class);
	    xstream.alias("weatherReport", WsData.WeatherReport.class);
	    xstream.alias("weatherForecast", WsData.WeatherForecast.class);
	    xstream.addImplicitCollection(WsData.WeatherReports.class, "wReports");
	    xstream.addImplicitCollection(WsData.WeatherForecasts.class, "wForecasts");
	    xstream.useAttributeFor(WsData.WeatherReport.class, "dateString");
	    xstream.aliasField("date", WsData.WeatherReport.class, "dateString");
	    xstream.useAttributeFor(WsData.WeatherReport.class, "wspeed");
	    xstream.aliasField("windspeed", WsData.WeatherReport.class, "wspeed");
	    xstream.omitField(WsData.WeatherReport.class, "date");
	    xstream.omitField(WsData.WeatherReports.class, "mapDateWindSpeed");
	    xstream.useAttributeFor(WsData.WeatherForecast.class, "dateString");
	    xstream.aliasField("date", WsData.WeatherForecast.class, "dateString");
	    xstream.useAttributeFor(WsData.WeatherForecast.class, "id");
	    xstream.aliasField("id", WsData.WeatherForecast.class, "id");
	    xstream.useAttributeFor(WsData.WeatherForecast.class, "originString");
	    xstream.aliasField("origin", WsData.WeatherForecast.class, "originString");
	    xstream.useAttributeFor(WsData.WeatherForecast.class, "temp");
	    xstream.aliasField("temp", WsData.WeatherForecast.class, "temp");
	    xstream.useAttributeFor(WsData.WeatherForecast.class, "windspeed");
	    xstream.aliasField("windspeed", WsData.WeatherForecast.class, "windspeed");
	    xstream.omitField(WsData.WeatherForecast.class, "date");
	    xstream.omitField(WsData.WeatherForecast.class, "origin");
	    xstream.omitField(WsData.WeatherForecast.class, "wsError");
	    xstream.omitField(WsData.WeatherForecast.class, "windSpeedObservation");
	    xstream.omitField(WsData.WeatherForecast.class, "noObservation");    
	    	
		return xstream;
	}
	
	/**
	 * Entry point for the application
	 * @param args: empty vector [no arguments necessary]
	 */
	public static void main(String[] args) {
		
		File[]  dataFiles = getDataFiles();
		
		if (dataFiles == null || dataFiles.length == 0) {
			System.out.println("No wind speed forecast data files found");
			return;
		}
		
		WsRMSEApp myApp = new WsRMSEApp();
		
		//process each file
		int filenum = 0;
		for (File f : dataFiles) {
			filenum++;
			//check if you can load this file in an XStream object
			XStream xstream = getConfiguredXStream();
			//build WsData object
			WsData wsData = (WsData)xstream.fromXML(f);
			wsData.convertToDate();	

			//add the WsData data in local collections
			myApp.addWsData(wsData);
			
		} // for each data file
	
		// check weather-forecast ids
		myApp.checkWfIds();
		
		// calculate Wind Speed RMSE
		myApp.calcWindSpeedRMSE();
		
		//Write Output to XML file
		myApp.writeRMSEtoXML();
		
		return;

	} //main function
	
	private void checkWfIds() {
		System.out.println("*** ID Check Started ***");
		int badId = 0;
		for (WsData.WeatherForecast wf: this.windSpeedForecasts.getWeatherForecasts()) {
			long origMilliS = wf.getOrigin().getMillis();
			long fcMilliS = wf.getDate().getMillis();
			if (origMilliS % 1000 > 0) {
				System.out.println("origin milisec not divisible by 1000");
			}
			if (fcMilliS % 1000 > 0) {
				System.out.println("forecast date milisec not divisible by 1000");
			}
			long origSec = origMilliS /1000;
			long fcSec = fcMilliS /1000;
			long diffSec = fcSec - origSec;
			if (diffSec % 3600 > 0) {
				System.out.println("difference is not whole hours");
			}
			int idShdBe = (int) (diffSec / 3600);
			
			if (idShdBe != wf.getId()) {
				badId++;
				System.out.println("ID = " + wf.getId() + " extected id = " + idShdBe);
				System.out.println("id not equal to what it should be");
			}
			
		} //f0r each forecast
		
		System.out.println("Total number of bad IDs = " + badId);
	}

	private void addWsData(WsData wsd) {
		this.windSpeedForecasts.addWeatherForecasts(wsd.getWeatherForecasts());
		this.windSpeedObservations.addWeatherReports(wsd.getWeatherReports());
		return;
	}
	
	private void calcWindSpeedRMSE() {
		
		//build necessary maps
		this.windSpeedObservations.bildMaps();
		
		//calculate wind speed errors for all forecasts
		this.windSpeedForecasts.calcWindSpeedForecastErrors(this.windSpeedObservations);
		
		//build empty map for lead times from 1 thru 50
		for (int i = 0; i < 50; i++) {
			this.mapLeadTimeRMSE.put(i+1, (float) 0.0);
			this.mapLtErrorSet.put(i+1, new LinkedHashSet<Float>());
		}
		
		// put all errors in the mapLtErrorSet
		for (WsData.WeatherForecast wf : this.windSpeedForecasts.getWeatherForecasts()) {
			int leadTime = wf.getLeadHours();
			if (leadTime > 50) {
				System.out.println("Lead Time greater than 50 hours found");
				continue;
			}
			float error = wf.getWindSPeedError();
			boolean validError = wf.windSpeedObservationAvailable();
			Set<Float> errorSet = this.mapLtErrorSet.get(leadTime);
			if (errorSet != null) {
				if (validError) {
					errorSet.add(error);
				}
			} else {
				System.out.println("Error: null value for lead time " + leadTime);
			}
		} //for each wind speed forecast
		
		//now for each lead time calculate RMSE
		for (int i = 0; i < 50; i++) {
			int key = i + 1;
			Set<Float> errorSet = this.mapLtErrorSet.get(key);
			if (errorSet != null) {
				double squareOfErrors = 0; //this can be a large number
				int num = 0;
				for (float err : errorSet) {
					squareOfErrors += err * err;
					num++;
				}
				double meanOfErrors = 0;
				if (num > 0) {
					meanOfErrors = squareOfErrors / num;
				}
				float rmse = 0;
				if (meanOfErrors > 0) {
					rmse = (float) Math.sqrt((double)meanOfErrors);
				}
				//add the rmse in map
				this.mapLeadTimeRMSE.put(key, rmse);
			}
		}
		// put results into XStream friendly object
		this.wspRmse = new WindSpeedRMSE();
		for (Map.Entry<Integer, Float> entry : this.mapLeadTimeRMSE.entrySet()) {
			
			int hour = entry.getKey();
			float val = entry.getValue();
			wspRmse.addRmseVal(hour, val);
		}
	}//calcWindSpeedRMSE()
	
	//write output to XML file
	public void writeRMSEtoXML() {
		if (this.wspRmse == null)
			return;

		XStream xstream = new XStream();
		xstream.alias("rmse_curve", WindSpeedRMSE.class);
		xstream.alias("rmse", WindSpeedRMSE.RmseVal.class);
		xstream.addImplicitCollection(WindSpeedRMSE.class, "rmseVals");
		xstream.useAttributeFor(WindSpeedRMSE.RmseVal.class, "hour");
		xstream.aliasField("hour", WindSpeedRMSE.RmseVal.class, "hour");
		xstream.useAttributeFor(WindSpeedRMSE.RmseVal.class, "value");
		xstream.aliasField("value", WindSpeedRMSE.RmseVal.class, "value");
		
		String xmlStr = xstream.toXML(this.wspRmse);
		String fileName = WsRMSEApp.wsRmsePath + "/" + WsRMSEApp.rmseFileName;
		
		try {
			FileWriter fw = new FileWriter(fileName);
			fw.write(xmlStr);
			fw.write("\n");
			fw.close();
		} catch (IOException ex) {
			System.out.println(ex);
		}
		
		System.out.println("======= Program Completed ============");
		return;
	} // writeRMSEtoXML()

} //class WsRMSEApp
