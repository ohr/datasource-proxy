package net.ttddyy.dsproxy.transform;

import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.jdk.ConnectionInvocationHandler;
import net.ttddyy.dsproxy.proxy.InterceptorHolder;
import net.ttddyy.dsproxy.proxy.JdbcProxyFactory;
import net.ttddyy.dsproxy.proxy.jdk.StatementInvocationHandler;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Tadaya Tsuyukubo
 */
public class TransformInfoForQueryTest {

    private TransformInfo transformInfo;

    @BeforeMethod
    public void setUp() {
        transformInfo = null;
    }

    private QueryTransformer getMockQueryTransformer(int timesToAnswer) {
        Answer<String> answer = new Answer<String>() {
            public String answer(InvocationOnMock invocation) throws Throwable {
                transformInfo = (TransformInfo) invocation.getArguments()[0];
                return "modified-query";
            }
        };

        QueryTransformer queryTransformer = mock(QueryTransformer.class);
        for (int i = 0; i < timesToAnswer; i++) {
            when(queryTransformer.transformQuery(isA(TransformInfo.class))).then(answer);
        }

        return queryTransformer;
    }

    @Test
    public void testQueryTransformerInStatement() throws Throwable {

        Statement stmt = mock(Statement.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(1);
        InterceptorHolder interceptors = new InterceptorHolder(QueryExecutionListener.DEFAULT, queryTransformer);
        JdbcProxyFactory proxyFactory = mock(JdbcProxyFactory.class);

        StatementInvocationHandler handler = new StatementInvocationHandler(stmt, interceptors, "my-ds", proxyFactory);

        Method method = Statement.class.getMethod("execute", String.class);
        Object[] args = new Object[]{"my-query"};
        handler.invoke(null, method, args);

        verify(queryTransformer).transformQuery(isA(TransformInfo.class));

        assertThat(transformInfo, notNullValue());
        assertThat(transformInfo.getClazz(), typeCompatibleWith(Statement.class));
        assertThat(transformInfo.getQuery(), is("my-query"));
        assertThat(transformInfo.getDataSourceName(), is("my-ds"));
        assertThat(transformInfo.isBatch(), is(false));
        assertThat(transformInfo.getCount(), is(0));

    }


    @Test
    public void testQueryTransformerBatchInStatement() throws Throwable {

        Statement stmt = mock(Statement.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(2);
        InterceptorHolder interceptors = new InterceptorHolder(QueryExecutionListener.DEFAULT, queryTransformer);
        JdbcProxyFactory proxyFactory = mock(JdbcProxyFactory.class);

        StatementInvocationHandler handler = new StatementInvocationHandler(stmt, interceptors, "my-ds", proxyFactory);

        Method method = Statement.class.getMethod("addBatch", String.class);

        // first batch
        handler.invoke(null, method, new Object[]{"my-query-1"});

        verify(queryTransformer).transformQuery(isA(TransformInfo.class));
        assertThat(transformInfo, notNullValue());
        assertThat(transformInfo.getClazz(), typeCompatibleWith(Statement.class));
        assertThat(transformInfo.getQuery(), is("my-query-1"));
        assertThat(transformInfo.getDataSourceName(), is("my-ds"));
        assertThat(transformInfo.isBatch(), is(true));
        assertThat(transformInfo.getCount(), is(0));

        // second batch
        handler.invoke(null, method, new Object[]{"my-query-2"});

        verify(queryTransformer, times(2)).transformQuery(isA(TransformInfo.class));
        assertThat(transformInfo, notNullValue());
        assertThat(transformInfo.getClazz(), typeCompatibleWith(Statement.class));
        assertThat(transformInfo.getQuery(), is("my-query-2"));
        assertThat(transformInfo.getDataSourceName(), is("my-ds"));
        assertThat(transformInfo.isBatch(), is(true));
        assertThat(transformInfo.getCount(), is(1));

    }

    @DataProvider
    private Object[][] connectionHandlerTestData() {
        return new Object[][]{
                {"prepareStatement", PreparedStatement.class},
                {"prepareCall", CallableStatement.class}
        };
    }


    @Test(dataProvider = "connectionHandlerTestData")
    public void testQueryTransformerInConnectionHandler(String methodName, Class<? extends PreparedStatement> callingClass) throws Throwable {

        Connection conn = mock(Connection.class);
        QueryTransformer queryTransformer = getMockQueryTransformer(1);
        InterceptorHolder interceptors = new InterceptorHolder(QueryExecutionListener.DEFAULT, queryTransformer);
        JdbcProxyFactory proxyFactory = mock(JdbcProxyFactory.class);
        ConnectionInvocationHandler handler = new ConnectionInvocationHandler(conn, interceptors, "my-ds", proxyFactory);

        Method method = Connection.class.getMethod(methodName, String.class);

        handler.invoke(null, method, new Object[]{"my-query"});

        verify(queryTransformer).transformQuery(isA(TransformInfo.class));
        assertThat(transformInfo, notNullValue());
        assertThat(transformInfo.getClazz(), typeCompatibleWith(callingClass));
        assertThat(transformInfo.getQuery(), is("my-query"));
        assertThat(transformInfo.getDataSourceName(), is("my-ds"));
        assertThat(transformInfo.isBatch(), is(false));
        assertThat(transformInfo.getCount(), is(0));

    }

}