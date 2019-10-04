package com.jcdecaux.datacorp.spark

import com.jcdecaux.datacorp.spark.annotation.Delivery
import com.jcdecaux.datacorp.spark.config.ConfigLoader
import com.jcdecaux.datacorp.spark.storage.Condition
import com.jcdecaux.datacorp.spark.storage.connector.FileConnector
import com.jcdecaux.datacorp.spark.storage.repository.SparkRepository
import com.jcdecaux.datacorp.spark.transformation.Factory
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.{Dataset, SparkSession, functions}
import org.scalatest.FunSuite

class DCContextSuite extends FunSuite {

  val configLoader: ConfigLoader = ConfigLoader.builder()
    .setAppEnv("local")
    .setAppName("Test ConfigLoader builder")
    .getOrCreate()

  val ds: Seq[TestObject] = Seq(
    TestObject(1, "a", "A", 1L),
    TestObject(2, "b", "B", 2L)
  )

  test("DCContext should build a spark session") {
    val context: DCContext = DCContext.builder()
      .setConfigLoader(configLoader)
      .setDCContextConfigPath("context")
      .getOrCreate()

    val ss = context.spark

    println(configLoader.appName)
    println(ss.sparkContext.appName)
    println(ss.sparkContext.getConf.get("spark.app.name"))
  }

  test("DCContext should be able to create SparkRepository") {
    val context: DCContext = DCContext.builder()
      .setConfigLoader(configLoader)
      .setDCContextConfigPath("context")
      .getOrCreate()

    val ss = context.spark

    import ss.implicits._

    val ds = this.ds.toDS()

    val repo = context.getSparkRepository[TestObject]("csv_dc_context2")

    repo.save(ds)
    val read = repo.findAll()

    assert(read.count() === 2)
    assert(repo.findBy(Condition("partition1", "=", 1)).count() === 1)

    repo.getConnector.asInstanceOf[FileConnector].delete()
  }

  test("DCContext should be able to create a pipeline with all the registered spark repository") {
    val context = DCContext.builder()
      .setConfigLoader(configLoader)
      .getOrCreate()

    val repo1 = context.getSparkRepository[TestObject]("csv_dc_context")
    val repo2 = context.getSparkRepository[TestObject3]("parquet_dc_context")

    context
      .newPipeline()
      .addStage(classOf[DCContextSuite.MyFactory], context.spark)
      .addStage(classOf[DCContextSuite.MyFactory2], context.spark)
      .run()
      .getLastOutput.asInstanceOf[Dataset[TestObject3]]
      .show()

    assert(repo1.findAll().count() === 2)
    assert(repo2.findAll().count() === 2)
    assert(repo1.findAll().columns.length === 4)
    assert(repo2.findAll().columns.length === 5)

    repo1.getConnector.asInstanceOf[FileConnector].delete()
    repo2.getConnector.asInstanceOf[FileConnector].delete()

    assert(context.configLoader.configPath === None)
    assert(context.configLoader.appEnv === "local")
  }

  test("DCContext should be work with default config loader") {
    System.setProperty("app.environment", "local")
    val context = DCContext.builder()
      .withDefaultConfigLoader("myconf.conf")
      .getOrCreate()

    assert(context.configLoader.get("my_test_variable") === "haha")
    assert(context.configLoader.appEnv === "local")
    assert(context.configLoader.configPath === Some("myconf.conf"))
  }

  test("DCContext should be able to handle AppEnv setting with default config loader") {
    System.setProperty("app.environment", "test")
    assertThrows[java.lang.IllegalArgumentException](
      DCContext.builder().withDefaultConfigLoader("myconf.conf").getOrCreate(),
      "DCContext should throw an exception when the app.environment value is invalid"
    )
  }
}

object DCContextSuite {

  class MyFactory(spark: SparkSession) extends Factory[Dataset[TestObject]] {

    import spark.implicits._

    @Delivery
    var repo: SparkRepository[TestObject] = _
    var output: Dataset[TestObject] = Seq(
      TestObject(1, "a", "A", 1L),
      TestObject(2, "b", "B", 2L)
    ).toDS()

    override def read(): MyFactory.this.type = this

    override def process(): MyFactory.this.type = this

    override def write(): MyFactory.this.type = {
      repo.save(output)
      this
    }

    override def get(): Dataset[TestObject] = output
  }

  class MyFactory2(spark: SparkSession) extends Factory[Dataset[TestObject3]] {
    @Delivery
    var input: Dataset[TestObject] = _
    @Delivery
    var repo: SparkRepository[TestObject3] = _
    var output: Dataset[TestObject3] = _

    override def read(): MyFactory2.this.type = this

    override def process(): MyFactory2.this.type = {
      output = input
        .withColumn("value2", functions.lit("haha"))
        .as[TestObject3](ExpressionEncoder[TestObject3])
      this
    }

    override def write(): MyFactory2.this.type = {
      repo.save(output)
      this
    }

    override def get(): Dataset[TestObject3] = output
  }

}