package org.datapipeline;

import com.google.api.services.bigquery.model.TableRow;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.datapipeline.models.OrderItems;
import org.datapipeline.models.OrderShipments;
import org.datapipeline.models.OrderSources;
import org.datapipeline.models.OrderStatus;

import java.beans.PropertyVetoException;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import static org.datapipeline.models.OrderErrors.setParametersOrderErrorsSQL;
import static org.datapipeline.models.OrderItems.setParametersOrderItemsSQL;
import static org.datapipeline.models.OrderShipments.setParametersOrderShipmentsSQL;
import static org.datapipeline.models.OrderSources.setParametersOrderSourcesSQL;
import static org.datapipeline.models.OrderStatus.setParametersOrderStatusSQL;

public class TemplatePipelineDataToBigQueryNewStoreSQL {
    public static void main(String[] args) throws PropertyVetoException {

        String usernameSQL="cap5000";
        String passwordSQL="Mobilitech/20";
        String jdbcUrl="jdbc:mysql://51.91.122.200:3306/cap5000?useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC";
        //String jdbcUrl = "jdbc:mysql://google/cap5000?cloudSqlInstance=dkt-us-data-lake-a1xq:us-west2:mulesoftdbinstance-staging&socketFactory=com.google.cloud.sql.mysql.SocketFactory&user=cap5000&password=" + passwordSQL + "&useUnicode=true&characterEncoding=UTF-8";

        PipelineOptionsFactory.register(TemplateOptions.class);
        TemplateOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(TemplateOptions.class);
        Pipeline pipeline = Pipeline.create(options);

        PCollection<String> pCollectionDataJson = pipeline.apply("READ DATA IN JSON FILE", TextIO.read().from(options.getInputFile()));
        //PCollection<String> pCollectionDataJson = pipeline.apply("READ", TextIO.read().from("gs://dkt-us-ldp-baptiste-test/upload/missing_customer_info.json"));
        //PCollection<String> pCollectionDataJson = pipeline.apply("READ", TextIO.read().from("gs://dkt-us-ldp-baptiste-test/webhookNewStoreOrder-07_02_2021_19_14_55.json"));


        // ********************************************   ORDER STATUS TABLE   ********************************************
        PCollection<TableRow> rowsOrderStatus = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER STATUS", ParDo.of(new OrderStatus.TransformJsonParDoOrderStatusNewStore()));
        rowsOrderStatus.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_status (order_number,source,type,status,updated_at) values(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " type = VALUES(type),\n" +
                        " status= VALUES(status),\n" +
                        " updated_at = VALUES(updated_at) ")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderStatusSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER STATUS PAYMENT ERROR    ********************************************
        PCollection<TableRow> rowsOrderStatusErrors = rowsOrderStatus.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new OrderStatus.mapOrderStatusErrorNewStore()));
        rowsOrderStatusErrors.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_errors (order_number,error_type,updated_at,source) values(?,?,?,?)" +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderErrorsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER SHIPMENTS TABLE   ********************************************
        PCollection<TableRow> rowsOrderShipments = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER STATUS", ParDo.of(new OrderShipments.TransformJsonParDoOrderShipmentsNewStore()));
        rowsOrderShipments.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_shipments (id,source,order_number,status,updated_at) values(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " order_number= VALUES(order_number),\n" +
                        " status= VALUES(status),\n" +
                        " updated_at = VALUES(updated_at) ")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderShipmentsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER SHIPMENTS ERROR    ********************************************
        PCollection<TableRow> rowsOrderShipmentsErrors = rowsOrderShipments.apply("TRANSFORM JSON TO TABLE ROW ERROR", ParDo.of(new OrderShipments.mapOrderShipmentsErrorNewStore()));
        rowsOrderShipmentsErrors.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_errors (order_number,error_type,updated_at,source) values(?,?,?,?) ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderErrorsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER SOURCES TABLE   ********************************************
        PCollection<TableRow> rowsOrderSources = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SOURCES", ParDo.of(new OrderSources.TransformJsonParDoOrderSourcesNewStore()));
        rowsOrderSources.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_sources (order_number,source,updated_at) values(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at) ")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderSourcesSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER ITEMS TABLE   ********************************************
        PCollection<TableRow> rowsOrderItems = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER ITEMS", ParDo.of(new OrderItems.TransformJsonParDoOrderItemsNewStore()));
        rowsOrderItems.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_items (id,shipment_id,source,name,price,quantity,updated_at) values(?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {

                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderItemsSQL(element, preparedStatement);
                    }
                })
        );

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


}
