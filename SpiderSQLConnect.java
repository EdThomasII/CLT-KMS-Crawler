/*
 *    Class: SpiderSQLConnect
 *   Author: Ed Thomas
 * Modified: 7-July-2016
 *  Purpose:
 *
 *
 */

import java.sql.Connection;
//import java.sql.ResultSet;
import java.sql.SQLException;
//import java.sql.Statement;
import java.sql.DriverManager;


public class SpiderSQLConnect
{
    public static final String URL = "jdbc:mysql://localhost/CLT_KB";
    public static final String USER = "SpiderRoot";
    public static final String PASSWORD = "BvKzGhM5Vsq6fHbV"; // not good form...
    public static final String DRIVER_CLASS = "com.mysql.jdbc.Driver";

    Connection connection;

    /*
     *   method: SpiderSQLConnect
     * modified: 15-January-2015
     *  purpose: Simple constructor that creates the class and sets the
     *           connection variable to null.
     */
    public SpiderSQLConnect() {
	connection = null;
    } // SpiderSQLConnect constructor


    /*
     *   method: dbConnect
     *   author: Ed Thomas
     * modified: 15-January-2015
     *  purpose: Creates and returns a connection to the MySql CLT_KBdatabase
     *
     */
    public Connection dbConnect() {
	try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
	try {
	    connection = DriverManager.getConnection(URL, USER, PASSWORD);
	} catch (SQLException e) {
            System.out.println("ERROR: Unable to Connect to Database."); 
	    System.out.println("Exception: " + e.getMessage());
        }
	return connection;
    } //dbConnect


    /*
     *   method: dbConnect
     *   author: Ed Thomas
     * modified: 15-January-2015
     *  purpose: Closes the connection to the MySql Spider database
     *
     */
    public void dbClose() {
	try {
	    connection.close();
	} catch (SQLException e) {
            System.out.println("ERROR: Unable to close database connection.");
	    System.out.println("Exception: " + e.getMessage());
        }	
    } // dbClose

} // SpiderSQLConnect class
