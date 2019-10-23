package com.jcdecaux.datacorp.spark.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class ConfLoaderSuite extends FunSuite with BeforeAndAfterAll {

  test("ConfigLoader builder should take into account the app.environment property in pom") {
    System.clearProperty("app.environment")
    val cl = ConfigLoader.builder().getOrCreate()
    assert(cl.appEnv === ConfigFactory.load().getString("app.environment"))
  }

  test("ConfigLoader builder should build configloader") {
    System.setProperty("app.environment", "test")
    System.setProperty("myvalue", "test-my-value")

    val cl = ConfigLoader.builder()
      .setAppEnv("local")
      .setAppName("TestConfigLoaderBuilder")
      .setProperty("myJvmProperty", "myJvmPropertyValue")
      .getOrCreate()

    assert(cl.get("test.string") === "foo")
    assert(cl.get("test.variable") === "myJvmPropertyValue")

    System.clearProperty("app.environment")
    System.clearProperty("myvalue")
  }

  test("ConfigLoader builder should prioritize setConfigPath than setAppEnv and jvm property and pom") {

    System.setProperty("app.environment", "test")

    val cl = ConfigLoader.builder()
      .setAppEnv("local")
      .setConfigPath("test_priority.conf")
      .getOrCreate()

    assert(cl.get("my.value") === "haha")
    System.clearProperty("app.environment")

  }

  test("ConfigLoader builder should prioritize setAppEnv than jvm property and pom") {
    System.setProperty("app.environment", "test")

    val cl = ConfigLoader.builder()
      .setAppEnv("test_priority")
      .getOrCreate()

    assert(cl.get("my.value") === "haha")
    System.clearProperty("app.environment")
  }

  test("ConfigLoader builder should prioritize jvm property than pom") {
    System.setProperty("app.environment", "test_priority")

    val cl = ConfigLoader.builder()
      .getOrCreate()

    assert(cl.get("my.value") === "haha")
    System.clearProperty("app.environment")
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    System.clearProperty("app.environment")
    System.clearProperty("myvalue")
  }
}
