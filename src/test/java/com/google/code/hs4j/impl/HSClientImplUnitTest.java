package com.google.code.hs4j.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.code.hs4j.FindOperator;
import com.google.code.hs4j.HSClient;
import com.google.code.hs4j.exception.HandlerSocketException;
import com.google.code.hs4j.network.core.impl.HandlerAdapter;
import com.google.code.hs4j.network.nio.TCPController;
import com.google.code.hs4j.network.util.ResourcesUtils;

public class HSClientImplUnitTest {

	private HSClient hsClient;
	private Properties props;

	@Before
	public void setUp() throws Exception {
		this.props = new Properties();
		InputStream in = null;
		try {
			in = ResourcesUtils.getResourceAsStream("jdbc.properties");
			this.props.load(in);
		} catch (IOException e) {

		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {

				}
			}
		}
		this.hsClient = new HSClientImpl(this.props.getProperty("hs.hostname"),
				Integer.parseInt(this.props.getProperty("hs.port")));
	}

	@After
	public void tearDown() throws IOException {
		this.hsClient.shutdown();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArguments1() throws Exception {
		final String[] columns = { "user_id", "user_name", "user_email", "age" };
		this.hsClient.openIndex(-1, null, "test_user", "NAME_MAIL_INDEX",
				columns);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArguments2() throws Exception {
		final String[] columns = { "user_id", "user_name", "user_email", "age" };
		String dbname = this.props.getProperty("hs.db");
		this.hsClient.openIndex(-1, dbname, "", "NAME_MAIL_INDEX", columns);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArguments3() throws Exception {
		final String[] columns = { "user_id", "user_name", "user_email", "age" };
		String dbname = this.props.getProperty("hs.db");
		this.hsClient.openIndex(-1, dbname, "test_user", null, columns);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalArguments4() throws Exception {
		String dbname = this.props.getProperty("hs.db");
		this.hsClient.openIndex(-1, dbname, "test_user", "NAME_MAIL_INDEX",
				null);
	}

	@Test
	public void testOpenIndexInsertFindUpdateFindDelete() throws Exception {
		int indexId = 1;
		String dbname = this.props.getProperty("hs.db");
		final String[] columns = { "user_id", "user_name", "user_email", "age" };

		assertTrue(this.hsClient.openIndex(indexId, dbname, "test_user",
				"NAME_MAIL_INDEX", columns));
		// insert
		assertTrue(this.hsClient.insert(indexId, new String[] { "0", "yuehua",
				"test@gmail.com", "25", "2010-11-28 13:24:00" }));

		// find once
		final String[] keys = { "yuehua", "test@gmail.com" };
		ResultSet rs = this.hsClient.find(indexId, keys);
		assertTrue(rs.next());

		System.out.println(rs.getInt(1));
		assertEquals("yuehua", rs.getString(2));
		assertEquals("test@gmail.com", rs.getString(3));
		assertEquals(25, rs.getInt(4));
		assertFalse(rs.next());

		assertEquals(1, this.hsClient.update(indexId, keys, new String[] { "0",
				"dennis", "killme2008@gmail.com", "100" }, FindOperator.EQ));

		rs = this.hsClient.find(indexId, keys);
		assertFalse(rs.next());

		rs = this.hsClient.find(indexId, new String[] { "dennis" });
		assertTrue(rs.next());

		System.out.println(rs.getInt(1));
		assertEquals("dennis", rs.getString(2));
		assertEquals("killme2008@gmail.com", rs.getString(3));
		assertEquals(100, rs.getInt(4));
		assertFalse(rs.next());

		assertEquals(1, this.hsClient.delete(indexId,
				new String[] { "dennis" }, FindOperator.EQ));
	}

	@Test
	public void testOpenIndexFindInsertFindFindDelete() throws Exception {
		int indexId = 1;
		String dbname = this.props.getProperty("hs.db");
		final String[] columns = { "user_id", "user_name", "user_email", "age" };

		assertTrue(this.hsClient.openIndex(indexId, dbname, "test_user",
				"NAME_MAIL_INDEX", columns));

		// find null
		final String[] keys = { "dennis", "killme2008@gmail.com" };
		ResultSet rs = this.hsClient.find(indexId, keys);
		assertFalse(rs.next());

		// insert
		assertTrue(this.hsClient.insert(indexId, new String[] { "0", "dennis",
				"killme2008@gmail.com", "27", "2010-11-28 13:24:00" }));

		// find once
		rs = this.hsClient.find(indexId, keys);
		assertTrue(rs.next());

		System.out.println(rs.getInt(1));
		assertEquals("dennis", rs.getString(2));
		assertEquals("killme2008@gmail.com", rs.getString(3));
		assertEquals(27, rs.getInt(4));
		assertFalse(rs.next());

		// find twice
		rs = this.hsClient.find(indexId, new String[] { "dennis" });
		assertTrue(rs.next());

		System.out.println(rs.getInt("user_id"));
		assertEquals("dennis", rs.getString("user_name"));
		assertEquals("killme2008@gmail.com", rs.getString("user_email"));
		assertEquals(27, rs.getInt("age"));
		assertFalse(rs.next());

		// delete
		assertEquals(1, this.hsClient.delete(indexId, keys, FindOperator.EQ));
		// find null
		rs = this.hsClient.find(indexId, keys);
		assertFalse(rs.next());

	}

	@Test
	public void testAutoReconnect() throws Exception {
		TCPController server = new TCPController();
		server.setHandler(new HandlerAdapter());
		server.bind(new InetSocketAddress(7171));

		this.hsClient.shutdown();
		this.hsClient = new HSClientImpl(new InetSocketAddress(7171), 5);

		Thread.sleep(1000);
		assertEquals(5, server.getSessionSet().size());
		server.stop();
		Thread.sleep(5000);
		server = new TCPController();
		server.setHandler(new HandlerAdapter());
		server.bind(new InetSocketAddress(7171));

		Thread.sleep(10000);
		assertEquals(5, server.getSessionSet().size());

	}

	@Test(expected = HandlerSocketException.class)
	public void testFindWithoutOpenIndex() throws Exception {
		// find null
		final String[] keys = { "dennis", "killme2008@gmail.com" };
		this.hsClient.find(1001, keys);
	}
}