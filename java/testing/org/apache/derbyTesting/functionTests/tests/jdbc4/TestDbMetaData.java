/*

   Derby - Class org.apache.derbyTesting.functionTests.tests.jdbc.TestDbMetaData

   Copyright 2006 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derbyTesting.functionTests.tests.jdbc4;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.RowIdLifetime;

import org.apache.derby.tools.ij;

/**
 * Test of database metadata for new methods in JDBC 40.
 */
public class TestDbMetaData { 

	public static void main(String[] args) {
		try
		{
            // Using TestConnection for now instead of ij because
            // ij.startJBMS() returns null for classes built against
            // JDK 1.6
            runTests(new TestConnection().createEmbeddedConnection());
            runTests(new TestConnection().createClientConnection());
        }
        catch (SQLException e) {
            dumpSQLExceptions(e);
        }
        catch (Throwable e) {
            System.out.println("FAIL -- unexpected exception:");
            e.printStackTrace(System.out);
        }
    }

    // Run all the tests.
    private static void runTests(Connection con) throws Exception {
        testDatabaseMetaDataMethods(con);
        testStoredProcEscapeSyntax(con);
        testAutoCommitFailure(con);
        con.close();
    }

    // Simply call each new metadata method and print the result.
    private static void testDatabaseMetaDataMethods(Connection con)
        throws Exception
    {
        con.setAutoCommit(true); // make sure it is true
        Statement s = con.createStatement();
        DatabaseMetaData met = con.getMetaData();

        if (!met.supportsStoredFunctionsUsingCallSyntax()) {
            System.out.println
                ("FAIL: supportsStoredFunctionsUsingCallSyntax() " +
                 "should return true");
        }

        if (met.autoCommitFailureClosesAllResultSets()) {
            System.out.println
                ("FAIL: autoCommitFailureClosesAllResultSets() " +
                 "should return false");
        }

        if (met.providesQueryObjectGenerator()) {
            System.out.println
                ("FAIL: providesQueryObjectGenerator() should " +
                 "return false");
        }

        RowIdLifetime lifetime = met.getRowIdLifetime();
        if (lifetime != RowIdLifetime.ROWID_UNSUPPORTED) {
            System.out.println("FAIL: getRowIdLifetime() should return " +
                               "ROWID_UNSUPPORTED, but got " + lifetime);
        }

        try {
			checkEmptyRS(met.getClientInfoProperties());
        } catch (SQLException e) {
            // TODO: remove try/catch once method is implemented!
            System.out.println("getClientInfoProperties():");
            dumpSQLExceptions(e);
        }

        // Create some functions in the default schema (app) to make
        // the output from getFunctions() and getFunctionParameters
        // more interesting
        s.execute("CREATE FUNCTION DUMMY1 ( X SMALLINT ) RETURNS SMALLINT "+
                  "PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA EXTERNAL "+
                  "NAME 'java.some.func'");
        s.execute("CREATE FUNCTION DUMMY2 ( X INTEGER, Y SMALLINT ) RETURNS"+
                  " INTEGER PARAMETER STYLE JAVA NO SQL LANGUAGE JAVA "+
                  "EXTERNAL NAME 'java.some.func'");
        s.execute("CREATE FUNCTION DUMMY3 ( X VARCHAR(16), Y INTEGER ) "+
                  "RETURNS VARCHAR(16) PARAMETER STYLE JAVA NO SQL LANGUAGE"+
                  " JAVA EXTERNAL NAME 'java.some.func'");
        s.execute("CREATE FUNCTION DUMMY4 ( X VARCHAR(128), Y INTEGER ) "+
                  "RETURNS INTEGER PARAMETER STYLE JAVA NO SQL LANGUAGE "+
                  "JAVA EXTERNAL NAME 'java.some.func'");

        try {
			checkEmptyRS(met.getFunctionParameters(null,null,null,null));
        } catch (SQLException e) {
            // TODO: remove try/catch once method is implemented!
            System.out.println("getFunctionParameters():");
            dumpSQLExceptions(e);
        } catch (AbstractMethodError ame) {
            // TODO: No implementation on client yet, so catch
            // AbstractMethodError for now. Remove when implemented.
            System.out.println("getFunctionParameters():");
            ame.printStackTrace(System.out);
        }

        try {
			// Any function in any schema in any catalog
			dumpRS(met.getFunctions(null, null, null));
			// Any function in any schema in "Dummy
			// Catalog". Same as above since the catalog
			// argument is ignored (is always null)
			dumpRS(met.getFunctions("Dummy Catalog", null, null));
			// Any function in a schema starting with "SYS"
			dumpRS(met.getFunctions(null, "SYS%", null));
			// All functions containing "GET" in any schema 
			// (and any catalog)
			dumpRS(met.getFunctions(null, null, "%GET%"));
			// Any function that belongs to NO schema and 
			// NO catalog (none)
			checkEmptyRS(met.getFunctions("", "", null));
            
        } catch (SQLException e) {
            // TODO: remove try/catch once method is implemented!
            System.out.println("getFunctions():");
            dumpSQLExceptions(e);
        } catch (AbstractMethodError ame) {
            // TODO: No implementation on client yet, so catch
            // AbstractMethodError for now. Remove when implemented.
            System.out.println("getClientInfoProperties():");
            ame.printStackTrace(System.out);
        }

        try {
            // 
            // Test the new getSchemas() with no schema qualifiers
            //
            dumpRS(met.getSchemas(null, null));
            
            //
            // Test the new getSchemas() with a schema wildcard qualifier
            // 
            dumpRS(met.getSchemas(null, "SYS%"));
            
            // 
            // Test the new getSchemas() with an exact match
            //
            dumpRS(met.getSchemas(null, "APP"));
            
            //
            // Make sure that getSchemas() returns an empty result
            // set when a schema is passed with no match
            //
            checkEmptyRS(met.getSchemas(null, "BLAH"));
        } catch (SQLException e) {
            // TODO: remove try/catch once method is implemented!
            System.out.println("getSchemas():");
            dumpSQLExceptions(e);
        }

        s.close();
    }

    /**
     * Test supportsStoredFunctionsUsingCallSyntax() by checking
     * whether calling a stored procedure using the escape syntax
     * succeeds.
     *
     * @param con <code>Connection</code> object used in test
     * @exception SQLException if an unexpected database error occurs
     */
    private static void testStoredProcEscapeSyntax(Connection con)
        throws SQLException
    {
        con.setAutoCommit(false);
        String call = "{CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(0)}";
        Statement stmt = con.createStatement();

        boolean success;
        try {
            stmt.execute(call);
            success = true;
        } catch (SQLException e) {
            success = false;
        }

        DatabaseMetaData dmd = con.getMetaData();
        boolean supported = dmd.supportsStoredFunctionsUsingCallSyntax();
        if (success != supported) {
            System.out.println("supportsStoredFunctionsUsingCallSyntax() " +
                               "returned " + supported + ", but executing " +
                               call + (success ? " succeeded." : " failed."));
        }
        stmt.close();
        con.rollback();
    }

    /**
     * Test autoCommitFailureClosesAllResultSets() by checking whether
     * a failure in auto-commit mode will close all result sets, even
     * holdable ones.
     *
     * @param con <code>Connection</code> object used in test
     * @exception SQLException if an unexpected database error occurs
     */
    private static void testAutoCommitFailure(Connection con)
        throws SQLException
    {
        DatabaseMetaData dmd = con.getMetaData();
        boolean shouldBeClosed = dmd.autoCommitFailureClosesAllResultSets();

        con.setAutoCommit(true);

        Statement s1 =
            con.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                ResultSet.CONCUR_READ_ONLY,
                                ResultSet.HOLD_CURSORS_OVER_COMMIT);
        ResultSet resultSet = s1.executeQuery("VALUES (1, 2), (3, 4)");

        Statement s2 = con.createStatement();
        try {
            String query =
                "SELECT dummy, nonexistent, phony FROM imaginarytable34521";
            s2.execute(query);
            System.out.println("\"" + query + "\" is expected to fail, " +
                               "but it didn't.");
        } catch (SQLException e) {
            // should fail, but we don't care how
        }

        boolean isClosed = resultSet.isClosed();
        if (isClosed != shouldBeClosed) {
            System.out.println("autoCommitFailureClosesAllResultSets() " +
                               "returned " + shouldBeClosed +
                               ", but ResultSet is " +
                               (isClosed ? "closed." : "not closed."));
        }
        resultSet.close();
        s1.close();
        s2.close();
    }

	static private void dumpSQLExceptions (SQLException se) {
		System.out.println("FAIL -- unexpected exception");
		while (se != null) {
			System.out.print("SQLSTATE("+se.getSQLState()+"):");
			se.printStackTrace(System.out);
			se = se.getNextException();
		}
	}

	static void dumpRS(ResultSet s) throws SQLException {
		ResultSetMetaData rsmd = s.getMetaData ();

		// Get the number of columns in the result set
		int numCols = rsmd.getColumnCount ();

		if (numCols <= 0) {
			System.out.println("(no columns!)");
			return;
		}
		
		// Display column headings
		for (int i=1; i<=numCols; i++) {
			if (i > 1) System.out.print(",");
			System.out.print(rsmd.getColumnLabel(i));
		}
		System.out.println();
	
		// Display data, fetching until end of the result set
		while (s.next()) {
			// Loop through each column, getting the
			// column data and displaying
			for (int i=1; i<=numCols; i++) {
				if (i > 1) System.out.print(",");
				System.out.print(s.getString(i));
			}
			System.out.println();
		}
		s.close();
	}

	/**
	 * Checks for a ResultSet with no rows.
	 *
	 */
	static void checkEmptyRS(ResultSet rs) throws Exception
	{		
		boolean passed = false;

		try {
			if ( rs == null )
            {
                throw new Exception("Metadata result set can not be null");
            }
            int numrows = 0;
            while (rs.next())
                numrows++;
            // Zero rows is what we want.
            if (numrows != 0) {
                throw new Exception("Result set is not empty");
            }
		}
		catch (SQLException e)
		{
			throw new Exception("Unexpected SQL Exception: " + e.getMessage(), e);
		}
	}
}
