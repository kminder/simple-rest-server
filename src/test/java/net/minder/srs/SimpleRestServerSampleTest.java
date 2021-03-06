/**
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
package net.minder.srs;

import com.jayway.restassured.RestAssured;
import net.minder.util.JavaProcessPreBuilder;
import net.minder.util.PortUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SimpleRestServerSampleTest {

  private static URI uri = null;
  private static Process proc = null;

  @BeforeClass
  public static void setUpSuite() throws IOException, URISyntaxException {
    uri = new URI( "http", null, "localhost", PortUtils.getFreePort(), "/", null, null );

    JavaProcessPreBuilder pb = new JavaProcessPreBuilder();
    pb.inheritClassPath();
    pb.inheritIO();
    pb.main( SimpleRestServerSample.class );
    pb.args( Integer.toString( uri.getPort() ) );
    proc = pb.start();
    PortUtils.awaitOpenPort( uri );

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

    SimpleRestServerSample.Output output = client
        .target( uri )
        .path( "query" )
        .request( MediaType.APPLICATION_JSON_TYPE )
        .get( SimpleRestServerSample.Output.class );
    assertThat( output.id, is( "test-id" ) );

    SimpleRestServerSample.Input input = new SimpleRestServerSample.Input();
    input.name = "test-name";
    input.args = new String[]{ "test-arg1", "test-arg2" };
    String status = client
        .target( uri )
        .path( "exec" )
        .request( MediaType.TEXT_PLAIN )
        .post( Entity.entity( input, MediaType.APPLICATION_JSON_TYPE ), String.class );
    assertThat( status, is( "ok" ) );

    input = new SimpleRestServerSample.Input();
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
    SimpleRestServerSample.Input input = new SimpleRestServerSample.Input();

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

    SimpleRestServerSample.Output output = RestAssured.given()
        .body( input )
        .when()
        .get( "/query" )
        .then()
        .contentType( "application/json" )
        .statusCode( 200 )
        .extract()
        .as( SimpleRestServerSample.Output.class );
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
