package org.datapipeline.models;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import org.apache.beam.sdk.transforms.DoFn;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.datapipeline.utils.DateNow;

import java.sql.PreparedStatement;
import java.util.*;

import static org.datapipeline.utils.JsonToTableRow.convertJsonToTableRow;

public class OrderSources {

    public static TableSchema getTableSchemaOrderSources() {
        List<TableFieldSchema> fields = new ArrayList<>();
        fields.add(new TableFieldSchema().setName("order_number").setType("STRING").setMode("REQUIRED"));
        fields.add(new TableFieldSchema().setName("source").setType("STRING").setMode("REQUIRED"));
        fields.add(new TableFieldSchema().setName("updated_at").setType("DATETIME").setMode("REQUIRED"));
        return new TableSchema().setFields(fields);
    }

    public static void setParametersOrderSourcesSQL(TableRow element, PreparedStatement preparedStatement) throws Exception {
        preparedStatement.setString(1, element.get("order_number").toString());
        preparedStatement.setString(2, element.get("source").toString());
        preparedStatement.setString(3, element.get("updated_at").toString());
    }

    public static class TransformJsonParDoOrderSourcesShopify extends DoFn<String, TableRow> {

        @ProcessElement
        public void processElement(ProcessContext c) throws Exception {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(c.element());
            JSONObject jsonObject = (JSONObject) obj;
            JSONObject order = (JSONObject) jsonObject.get("order");

            Map<String, Object> mapOrderSources = new HashMap<>();
            mapOrderSources.put("order_number", order.get("name"));
            mapOrderSources.put("source","shopify");
            mapOrderSources.put("updated_at", DateNow.dateNow());

            JSONObject mapOrderSourcesToBigQuery = new JSONObject(mapOrderSources);
            TableRow tableRow = convertJsonToTableRow(String.valueOf(mapOrderSourcesToBigQuery));

            c.output(tableRow);
        }
    }

    public static class TransformJsonParDoOrderSourcesNewStore extends DoFn<String, TableRow> {

        @ProcessElement
        public void processElement(ProcessContext c) throws Exception {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(c.element());
            JSONObject jsonObject = (JSONObject) obj;

            Map<String, Object> mapOrderSources = new HashMap<>();
            mapOrderSources.put("order_number", jsonObject.get("order_id"));
            mapOrderSources.put("source","newstore");
            mapOrderSources.put("updated_at", DateNow.dateNow());

            JSONObject mapOrderSourcesToBigQuery = new JSONObject(mapOrderSources);
            TableRow tableRow = convertJsonToTableRow(String.valueOf(mapOrderSourcesToBigQuery));

            c.output(tableRow);
        }
    }

    public static class TransformJsonParDoOrderSourcesShipHawk extends DoFn<String, TableRow> {

        @ProcessElement
        public void processElement(ProcessContext c) throws Exception {
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(c.element());
            JSONObject jsonObject = (JSONObject) obj;

            Map<String, Object> mapOrderSources = new HashMap<>();
            String orderNumber = jsonObject.get("order_number").toString();
            String[] splitOrderNumber = orderNumber.split("-");
            mapOrderSources.put("order_number",splitOrderNumber[0]);
            mapOrderSources.put("source","shiphawk");
            mapOrderSources.put("updated_at", DateNow.dateNow());

            JSONObject mapOrderSourcesToBigQuery = new JSONObject(mapOrderSources);
            TableRow tableRow = convertJsonToTableRow(String.valueOf(mapOrderSourcesToBigQuery));

            c.output(tableRow);
        }
    }

    public static class TransformJsonParDoOrderSourcesSap extends DoFn<String, TableRow> {

        @ProcessElement
        public void processElement(ProcessContext c) throws Exception {

            List<TableRow> listTableRow = new ArrayList<>();
            JSONParser parser = new JSONParser();

            Object obj = parser.parse(c.element());
            JSONObject jsonObject = (JSONObject) obj;

            JSONArray statusArray = (JSONArray) jsonObject.get("status");

            Map<Object, Object> mapShipmentOrder = new HashMap<>();


            for (Object o : statusArray) {
                JSONObject status = (JSONObject) o;
                mapShipmentOrder.put("source", status.get("source"));
                mapShipmentOrder.put("order_number", status.get("order_number"));
                mapShipmentOrder.put("updated_at", DateNow.dateNow());
            }

            JSONObject mapShipmentOrderToBigQuery = new JSONObject(mapShipmentOrder);
            TableRow tableRowStatusFulfillment = convertJsonToTableRow(String.valueOf(mapShipmentOrderToBigQuery));
            listTableRow.add(tableRowStatusFulfillment);

            for (TableRow tableRow : listTableRow) {
                c.output(tableRow);
            }
        }
    }
}

