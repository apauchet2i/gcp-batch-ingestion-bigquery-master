package org.datapipeline;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.joda.time.Duration;
import org.datapipeline.models.Customer.*;
import org.datapipeline.models.Orders.*;
import org.datapipeline.models.OrderItems.*;
import org.datapipeline.models.OrderShipments.*;
import org.datapipeline.models.OrderStatus.*;
import org.datapipeline.models.ShipmentTrackings.*;
import org.datapipeline.models.OrderSources.*;
import java.util.*;
import static org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED;
import static org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition.WRITE_APPEND;
import static org.datapipeline.models.Customer.getTableSchemaCustomer;
import static org.datapipeline.models.OrderErrors.getTableSchemaOrderErrors;
import static org.datapipeline.models.OrderItems.getTableSchemaOrderItems;
import static org.datapipeline.models.OrderShipments.getTableSchemaOrderShipments;
import static org.datapipeline.models.OrderSources.getTableSchemaOrderSources;
import static org.datapipeline.models.OrderStatus.getTableSchemaOrderStatus;
import static org.datapipeline.models.Orders.*;
import static org.datapipeline.models.ShipmentTrackings.getTableSchemaShipmentTrackings;

public class TemplatePipelineDataToBigQueryShopify {
    public static void main(String[] args) {

        String project = "dkt-us-data-lake-a1xq";
        String dataset = "dkt_us_test_cap5000";

        PipelineOptionsFactory.register(TemplateOptions.class);
        TemplateOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(TemplateOptions.class);
        Pipeline pipeline = Pipeline.create(options);

        PCollection<String> pCollectionDataJson = pipeline.apply("READ DATA IN JSON FILE", TextIO.read().from(options.getInputFile()));
        //To test datapipeline in local environment
        //PCollection<String> pCollectionDataJson = pipeline.apply("READ", TextIO.read().from("gs://dkt-us-cap5000-project-platform-newevent/shopify/2021-02-15-14-10-16__newevent_shopify.json"));

         // ********************************************   ORDERS TABLE   ********************************************
        PCollection<TableRow> rowsOrders = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDERS", ParDo.of(new TransformJsonParDoOrders()));
        WriteResult writeResultOrders = rowsOrders
                .apply("WRITE DATA IN BIGQUERY ORDERS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.orders", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrder()));
        rowsOrders.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))

                .apply("FORMAT MESSAGE ORDER ERRORS",ParDo.of(new CountMessage("Orders_pipeline_completed","orders")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS",PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-orders"));

        // ********************************************   CUSTOMERS TABLE   ********************************************
        PCollection<TableRow> rowsCustomers = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new TransformJsonParDoCustomer()));
        WriteResult writeResultCustomers =rowsCustomers
                .apply("WRITE DATA IN BIGQUERY CUSTOMERS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.customers", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaCustomer()));
        rowsCustomers.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERRORS",ParDo.of(new CountMessage("Customers_pipeline_completed","customers")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS",PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-customers"));

        // ********************************************   CUSTOMERS ERRORS   ********************************************
        PCollection<TableRow> rowsCustomersError = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new mapOrderCustomersError()));
        WriteResult writeResultCustomersErrors = rowsCustomersError
                .apply("WRITE DATA IN BIGQUERY ERRORS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_errors", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderErrors()));
        rowsCustomersError.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERRORS",ParDo.of(new CountMessage("Customers_errors_pipeline_completed","order_errors")))
                        .apply("WRITE PUB MESSAGE ORDER ERRORS",PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-errors"));

        // ********************************************   ORDER ITEMS TABLE   ********************************************
        PCollection<List<TableRow>> rowsOrderItemsList = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER ITEMS", ParDo.of(new TransformJsonParDoOrderItemsShopifyList()));
        PCollection<TableRow> rowsOrderItems = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER ITEMS", ParDo.of(new TransformJsonParDoOrderItemsShopify()));
        WriteResult writeResultOrderItems =rowsOrderItems
                .apply("WRITE DATA IN BIGQUERY ORDER ITEMS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_items", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderItems()));
        rowsOrderItemsList.apply(Window.<List<TableRow>>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERRORS",ParDo.of(new CountMessageList("Order_items_pipeline_completed","order_items")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS",PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-items"));

        // ********************************************   ORDER SOURCES TABLE   ********************************************
        PCollection<TableRow> rowsOrderSources = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SOURCES", ParDo.of(new TransformJsonParDoOrderSourcesShopify()));
        WriteResult writeResultOrderSources =rowsOrderSources
                .apply("WRITE DATA IN BIGQUERY ORDER SOURCES TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_sources", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderSources()));
        rowsOrderSources.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))

                .apply("FORMAT MESSAGE ORDER ERRORS",ParDo.of(new CountMessage("Order_sources_pipeline_completed","order_sources")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS",PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-sources"));

        // ********************************************   ORDER STATUS TABLE   ********************************************
        PCollection<List<TableRow>> rowOrderStatusList = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER STATUS", ParDo.of(new TransformJsonParDoOrderStatusShopifyList()));
        PCollection<TableRow> rowsOrderStatus = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER STATUS", ParDo.of(new TransformJsonParDoOrderStatusShopify()));
        WriteResult writeResultOrderStatus =rowsOrderStatus
                .apply("WRITE DATA IN BIGQUERY ORDER STATUS TABLE", BigQueryIO.writeTableRows()
                .to(String.format("%s:%s.order_status", project,dataset))
                .withCreateDisposition(CREATE_IF_NEEDED)
                .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                .withSchema(getTableSchemaOrderStatus()));
        rowOrderStatusList.apply(Window.<List<TableRow>>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER STATUS", ParDo.of(new CountMessageList("Order_status_pipeline_completed","order_status")))
                .apply("WRITE PUB MESSAGE ORDER STATUS", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-status"));

        // ********************************************   ORDER STATUS PAYMENT ERROR    ********************************************
        PCollection<TableRow> rowsOrderStatusErrors = rowsOrderStatus.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new mapOrderStatusError()));
        WriteResult writeResultOrderStatusErrors =rowsOrderStatusErrors
                .apply("WRITE DATA IN BIGQUERY ORDER ERRORS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_errors", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderErrors()));
        rowsOrderStatusErrors.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERRORS", ParDo.of(new CountMessage("Order_status-errors_pipeline_completed","order_errors")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-errors"));

        // ********************************************   ORDER SHIPMENTS TABLE   ********************************************
        PCollection<List<TableRow>> rowsOrderShipmentsList = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SHIPMENTS", ParDo.of(new TransformJsonParDoOrderShipmentsShopifyList()));
        PCollection<TableRow> rowsOrderShipments = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SHIPMENTS", ParDo.of(new TransformJsonParDoOrderShipmentsShopify()));
        WriteResult writeResultOrderShipments =rowsOrderShipments
                .apply("WRITE DATA IN BIGQUERY ORDER SHIPMENTS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_shipments", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderShipments()));
        rowsOrderShipmentsList.apply(Window.<List<TableRow>>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER SHIPMENTS", ParDo.of(new CountMessageList("Order_shipments_pipeline_completed","order_shipments")))
                .apply("WRITE PUB MESSAGE ORDER SHIPMENTS", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-shipments"));

        // ********************************************   ORDER SHIPMENTS ERROR    ********************************************
        PCollection<TableRow> rowsOrderShipmentsErrors = rowsOrderShipments.apply("TRANSFORM JSON TO TABLE ROW ERROR", ParDo.of(new mapOrderShipmentsError()));
        WriteResult writeResultOrderShipmentsErrors =rowsOrderShipmentsErrors
                .apply("WRITE DATA IN BIGQUERY ERRORS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_errors", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderErrors()));
        rowsOrderShipmentsErrors.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERRORS", ParDo.of(new CountMessage("Order_status-errors_pipeline_completed","order_error")))
                .apply("WRITE PUB MESSAGE ORDER ERRORS", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-errors"));

        // ********************************************   SHIPMENT TRACKINGS TABLE   ********************************************
        PCollection<List<TableRow>> rowsShipmentTrackingsList = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW SHIPMENT TRACKINGS", ParDo.of(new TransformJsonParDoShipmentTrackingsShopifyList()));
        PCollection<TableRow> rowsShipmentTrackings = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW SHIPMENT TRACKINGS", ParDo.of(new TransformJsonParDoShipmentTrackingsShopify()));
        WriteResult writeResultShipmentTrackings =rowsShipmentTrackings
                .apply("WRITE DATA IN BIGQUERY SHIPMENT TRACKINGS TABLE", BigQueryIO.writeTableRows()
                    .to(String.format("%s:%s.shipment_trackings", project,dataset))
                    .withCreateDisposition(CREATE_IF_NEEDED)
                    .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                    .withSchema(getTableSchemaShipmentTrackings()));
        rowsShipmentTrackingsList.apply(Window.<List<TableRow>>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE SHIPMENT TRACKINGS", ParDo.of(new CountMessageList("Shipment_trackings_pipeline_completed","shipment_trackings")))
                .apply("WRITE PUB MESSAGE SHIPMENT TRACKINGS", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-shipment-trackings"));

        // ********************************************   SHIPMENT TRACKINGS ERROR   ********************************************
        PCollection<TableRow> rowsShipmentTrackingsError = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ERRORS", ParDo.of(new mapShipmentTrackingErrorShopify()));
        WriteResult writeResultShipmentTrackingsError =rowsShipmentTrackingsError
                .apply("WRITE DATA IN BIGQUERY ORDER ERRORS TABLE", BigQueryIO.writeTableRows()
                        .to(String.format("%s:%s.order_errors", project,dataset))
                        .withCreateDisposition(CREATE_IF_NEEDED)
                        .withWriteDisposition(WRITE_APPEND)
                        .optimizedWrites()
                        .withSchema(getTableSchemaOrderErrors()));
        rowsOrderShipmentsErrors.apply(Window.<TableRow>into(FixedWindows.of(Duration.standardMinutes(5))).triggering(AfterProcessingTime.pastFirstElementInPane().plusDelayOf(Duration.standardMinutes(5))).discardingFiredPanes().withAllowedLateness(Duration.standardMinutes(5)))
                .apply("FORMAT MESSAGE ORDER ERROR", ParDo.of(new CountMessage("Shipment-trackings-errors_pipeline_completed","order_errors")))
                .apply("WRITE PUB MESSAGE ORDER ERROR", PubsubIO.writeMessages().to("projects/dkt-us-data-lake-a1xq/topics/dkt-us-cap5000-project-datapipeline-order-errors"));

        pipeline.run();
    }

    public interface TemplateOptions extends DataflowPipelineOptions {
        @Description("GCS path of the file to read from")
        ValueProvider<String> getInputFile();
        void setInputFile(ValueProvider<String> value);
    }

    public static class CountMessage extends DoFn<TableRow, PubsubMessage>{
        private String messageDone;
        private String table;

        public CountMessage(String messageDone, String table) {
            this.messageDone = messageDone;
            this.table = table;
        }
        @ProcessElement
        public void processElement(ProcessContext c) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("table", table);
            PubsubMessage message = new PubsubMessage(messageDone.getBytes(), attributes);
            c.output(message);
        }
    }

    public static class CountMessageList extends DoFn<List<TableRow>, PubsubMessage>{
        private String messageDone;
        private String table;

        public CountMessageList(String messageDone, String table) {
            this.messageDone = messageDone;
            this.table = table;

        }
        @ProcessElement
        public void processElement(ProcessContext c) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("table", table);
            PubsubMessage message = new PubsubMessage(messageDone.getBytes(), attributes);
            c.output(message);
        }
    }
}
