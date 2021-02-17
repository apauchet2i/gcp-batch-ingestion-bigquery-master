package org.datapipeline;

import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.datapipeline.models.*;
import org.datapipeline.models.Orders.TransformJsonParDoOrders;

import java.beans.PropertyVetoException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.datapipeline.models.OrderErrors.setParametersOrderErrorsSQL;
import static org.datapipeline.models.OrderItems.setParametersOrderItemsSQL;
import static org.datapipeline.models.OrderShipments.setParametersOrderShipmentsSQL;
import static org.datapipeline.models.OrderSources.setParametersOrderSourcesSQL;
import static org.datapipeline.models.OrderStatus.setParametersOrderStatusSQL;
import static org.datapipeline.models.ShipmentTrackings.setParametersShipmentTrackingsSQL;

public class TemplatePipelineDataToBigQueryShopifySQL {
    public static void main(String[] args) throws PropertyVetoException, SQLException {

        String usernameSQL="cap5000";
        String passwordSQL="Mobilitech/20";
        String jdbcUrl = "jdbc:mysql://google/cap5000?cloudSqlInstance=dkt-us-data-lake-a1xq:us-west2:mulesoftdbinstance-staging&socketFactory=com.google.cloud.sql.mysql.SocketFactory&user=cap5000&password=" + passwordSQL + "&useUnicode=true&characterEncoding=UTF-8";

        PipelineOptionsFactory.register(TemplateOptions.class);
        TemplateOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().as(TemplateOptions.class);
        Pipeline pipeline = Pipeline.create(options);

        PCollection<String> pCollectionDataJson = pipeline.apply("READ DATA IN JSON FILE", TextIO.read().from(options.getInputFile()));
        //To test datapipeline in local environment
        //PCollection<String> pCollectionDataJson = pipeline.apply("READ", TextIO.read().from("gs://dkt-us-cap5000-project-platform-newevent/shopify/2021-02-16-18-01-07__newevent_shopify.json"));

         // ********************************************   ORDERS TABLE   ********************************************
        PCollection<TableRow> rowsOrders = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDERS", ParDo.of(new TransformJsonParDoOrders()));
        rowsOrders.apply(JdbcIO.<TableRow>write()
                        .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                                "com.mysql.jdbc.Driver", jdbcUrl))
                                .withStatement("insert into orders (number,customer_id,street1,street2,zip_code,city,country,created_at,updated_at) values(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                                        " customer_id = VALUES(customer_id)," +
                                        " street1= VALUES(street1)," +
                                        " street2= VALUES(street2)," +
                                        " zip_code= VALUES(zip_code)," +
                                        " city= VALUES(city)," +
                                        " country= VALUES(country)," +
                                        " created_at= VALUES(created_at)," +
                                        " updated_at = VALUES(updated_at)")
                                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                                    @Override
                                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                                        Orders.setParametersOrdersSQL(element, preparedStatement);
                                    }
                                })
                );

        // ********************************************   CUSTOMERS TABLE   ********************************************
        PCollection<TableRow> rowsCustomers = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new Customer.TransformJsonParDoCustomer()));
        rowsCustomers.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into customers (id,lastname,firstname,updated_at) values(?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE" +
                        " lastname = VALUES(lastname)," +
                        " firstname= VALUES(firstname)," +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {

                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        Customer.setParametersCustomers(element, preparedStatement);
                    }
                })
        );

        // ********************************************   CUSTOMERS ERRORS   ********************************************
        PCollection<TableRow> rowsCustomersError = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new Customer.mapOrderCustomersError()));
        rowsCustomersError.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_errors (order_number,error_type,updated_at,source) values(?,?,?,?)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {

                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderErrorsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER ITEMS TABLE   ********************************************
        PCollection<TableRow> rowsOrderItems = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER ITEMS", ParDo.of(new OrderItems.TransformJsonParDoOrderItemsShopify()));
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

        // ********************************************   ORDER SOURCES TABLE   ********************************************
        PCollection<TableRow> rowsOrderSources = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SOURCES", ParDo.of(new OrderSources.TransformJsonParDoOrderSourcesShopify()));
        rowsOrderSources.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                        .withStatement("insert into order_sources (order_number,source,updated_at) values(?,?,?) " +
                                "ON DUPLICATE KEY UPDATE \n" +
                                " updated_at = VALUES(updated_at) " +
                                "ON DUPLICATE KEY UPDATE \n" +
                                " updated_at = VALUES(updated_at)")
                        .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                            @Override
                            public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                                setParametersOrderSourcesSQL(element, preparedStatement);
                            }
                        })
        );

        // ********************************************   ORDER STATUS TABLE   ********************************************
        PCollection<TableRow> rowsOrderStatus = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER STATUS", ParDo.of(new OrderStatus.TransformJsonParDoOrderStatusShopify()));
        rowsOrderStatus.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_status (order_number,source,type,status,updated_at) values(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " type = VALUES(type),\n" +
                        " status= VALUES(status),\n" +
                        " updated_at = VALUES(updated_at) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderStatusSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER STATUS PAYMENT ERROR    ********************************************
        PCollection<TableRow> rowsOrderStatusErrors = rowsOrderStatus.apply("TRANSFORM JSON TO TABLE ROW CUSTOMERS", ParDo.of(new OrderStatus.mapOrderStatusError()));
        rowsOrderStatusErrors.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                        .withStatement("insert into order_sources (order_number,source,updated_at) values(?,?,?) " +
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
        PCollection<TableRow> rowsOrderShipments = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ORDER SHIPMENTS", ParDo.of(new OrderShipments.TransformJsonParDoOrderShipmentsShopify()));
        rowsOrderShipments.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_shipments (id,source,order_number,status,updated_at) values(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " order_number= VALUES(order_number),\n" +
                        " status= VALUES(status),\n" +
                        " updated_at = VALUES(updated_at) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersOrderShipmentsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   ORDER SHIPMENTS ERROR    ********************************************
        PCollection<TableRow> rowsOrderShipmentsErrors = rowsOrderShipments.apply("TRANSFORM JSON TO TABLE ROW ERROR", ParDo.of(new OrderShipments.mapOrderShipmentsError()));
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


        // ********************************************   SHIPMENT TRACKINGS TABLE   ********************************************
        PCollection<TableRow> rowsShipmentTrackings = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW SHIPMENT TRACKINGS", ParDo.of(new ShipmentTrackings.TransformJsonParDoShipmentTrackingsShopify()));
        rowsShipmentTrackings.apply(JdbcIO.<TableRow>write()
                .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                        "com.mysql.jdbc.Driver", jdbcUrl))
                .withStatement("insert into order_shipments (shipment_id,source,tracking_id,tracking_link,updated_at) values(?,?,?,?,?) ON DUPLICATE KEY UPDATE \n" +
                        " tracking_id = VALUES(tracking_id),\n" +
                        " tracking_link = VALUES(tracking_link),\n" +
                        " updated_at = VALUES(updated_at) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " order_number= VALUES(order_number),\n" +
                        " status= VALUES(status),\n" +
                        " updated_at = VALUES(updated_at) " +
                        "ON DUPLICATE KEY UPDATE \n" +
                        " updated_at = VALUES(updated_at)")
                .withPreparedStatementSetter(new JdbcIO.PreparedStatementSetter<TableRow>() {
                    @Override
                    public void setParameters(TableRow element, PreparedStatement preparedStatement) throws Exception {
                        setParametersShipmentTrackingsSQL(element, preparedStatement);
                    }
                })
        );

        // ********************************************   SHIPMENT TRACKINGS ERROR   ********************************************
        PCollection<TableRow> rowsShipmentTrackingsError = pCollectionDataJson.apply("TRANSFORM JSON TO TABLE ROW ERRORS", ParDo.of(new ShipmentTrackings.mapShipmentTrackingErrorShopify()));
        rowsShipmentTrackingsError.apply(JdbcIO.<TableRow>write()
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

        pipeline.run();
    }

    public interface TemplateOptions extends DataflowPipelineOptions {
        @Description("GCS path of the file to read from")
        ValueProvider<String> getInputFile();
        void setInputFile(ValueProvider<String> value);
    }

}