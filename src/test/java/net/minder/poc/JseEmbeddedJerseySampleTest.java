package net.minder.poc; /**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.jayway.restassured.RestAssured;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JseEmbeddedJerseySampleTest {

  private static URI uri = null;
  private static Process proc = null;

  private static int getFreePort() throws IOException {
    int port = 9999;
    try {
      ServerSocket socket = new ServerSocket( port );
      socket.close();
    } catch( IOException e ) {
      if( port > 1024 ) {
        port--;
      } else {
        throw e;
      }
    }
    return port;
  }

  private static String getJvm() {
    String jvm = "java";
    String sep = System.getProperty( "file.separator" );
    String home = System.getProperty( "java.home" );
    if( home != null && !home.isEmpty() ) {
      jvm = home + sep + "bin" + sep + "java";
    }
    return jvm;
  }

  private static String getClassPath() {
    return System.getProperty("java.class.path");
  }

  private static void waitForServerPort() {
    while( true ) {
      try {
        Socket s = new Socket( uri.getHost(), uri.getPort() );
        s.close();
        break;
      } catch( IOException e ) {
        // Ignore and wait.
        try {
          Thread.sleep( 10 );
        } catch( InterruptedException e1 ) {
          // Ignore.
        }
      }
    }
  }

  @BeforeClass
  public static void setUpSuite() throws IOException, URISyntaxException {
    uri = new URI( "http", null, "localhost", getFreePort(), "/", null, null );
    ProcessBuilder pb = new ProcessBuilder();
    List<String> args = new ArrayList();
    args.add( getJvm() );
    args.add( "-cp" );
    args.add( getClassPath() );
    args.add( JseEmbeddedJerseySample.class.getName() );
    args.add( Integer.toString( uri.getPort() ) );
    pb.command( args );
    proc = pb.start();
    waitForServerPort();

    Client client = ClientBuilder.newClient();
    String entity = client
        .target( uri )
        .path( "ping" )
        .request( MediaType.TEXT_PLAIN_TYPE )
        .get( String.class );
    client.close();
    assertThat( entity, is( "hello" ) );
  }

  @Test
  public void testWithJerseyClient() throws IOException {
    Client client = ClientBuilder.newClient();

    JseEmbeddedJerseySample.Output output = client
        .target( uri )
        .path( "query" )
        .request( MediaType.APPLICATION_JSON_TYPE )
        .get( JseEmbeddedJerseySample.Output.class );
    System.out.println( output );

    JseEmbeddedJerseySample.Input input = new JseEmbeddedJerseySample.Input();
    input.name = "test-name";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    String status = client
        .target( uri )
        .path( "exec" )
        .request( MediaType.TEXT_PLAIN )
        .post( Entity.entity( input, MediaType.APPLICATION_JSON_TYPE ), String.class );
    assertThat( status, is( "ok" ) );

    input = new JseEmbeddedJerseySample.Input();
    input.name = "invalid-test-command";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    try {
      client
          .target( uri )
          .path( "exec" )
          .request( MediaType.TEXT_PLAIN )
          .post( Entity.entity( input, MediaType.APPLICATION_JSON_TYPE ), String.class );
    } catch( ServerErrorException e ) {
      // Expected.
    }

    client.close();
  }

  @Test
  public void testWithRestAssured() throws IOException {
    RestAssured.baseURI = uri.toString();
    JseEmbeddedJerseySample.Input input = new JseEmbeddedJerseySample.Input();

    input.name = "invalid-test-name";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    RestAssured.given()
        .contentType( "application/json" )
        .body( input )
        .when()
        .post( "/exec" )
        .then()
        .statusCode( 517 );

    input.name = "invalid-test-name";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    String s = RestAssured.given()
        //.log().all()
        .contentType( "application/json" )
        .body( input )
        .when()
        .post( "/exec" )
        .then()
        //.log().all()
        .statusCode( 517 )
        .extract()
        .response().asString();
    assertThat( s, containsString( "but: was \"invalid-test-name\"" ) );

    input.name = "test-name";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    RestAssured.given()
        .contentType( "application/json" )
        .body( input )
        .when()
        .post( "/exec" )
        .then()
        .contentType( "text/plain" )
        .body( is( "ok" ) )
        .statusCode( 200 );

    JseEmbeddedJerseySample.Output output = RestAssured.given()
        .body( input )
        .when()
        .get( "/query" )
        .then()
        .contentType( "application/json" )
        .statusCode( 200 )
        .extract()
        .as( JseEmbeddedJerseySample.Output.class );
    assertThat( output.id, is( "test-id" ) );
  }

  @AfterClass
  public static void tearDownSuite() throws InterruptedException {
    Client client = ClientBuilder.newClient();
    String status = client
        .target( uri )
        .path( "exit" )
        .request( MediaType.TEXT_PLAIN_TYPE )
        .get( String.class );
    client.close();
    assertThat( status, is( "ok" ) );
    proc.waitFor();
  }

}
