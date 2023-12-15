package application;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ObjednavkyServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    String URL = "jdbc:mysql://localhost:3307/";
    String databaza = "obchod";
    String userName = "root";
    String pass = "";
    Connection con;
    String errorMessage = "";
    
    public class Guard implements HttpSessionBindingListener {
  	  Connection connection;
  	  
  	 public Guard(Connection c) {
  	    connection = c; 
  	 }

  	 @Override
  	 public void valueBound(HttpSessionBindingEvent event) {}

  	 @Override
  	 public void valueUnbound(HttpSessionBindingEvent event) {
  	    try { 
  	       if (connection != null) connection.close(); 
  	    } catch (Exception e) { }
  	  }            
  	}
    
    protected Connection dajSpojenie(HttpServletRequest request) {
        try {
         HttpSession session = request.getSession();
          con = (Connection)session.getAttribute("spojenie"); 
          if (con == null) { 
        	Class.forName("com.mysql.cj.jdbc.Driver");
        	con = DriverManager.getConnection(URL + databaza, userName, pass);
            session.setAttribute("spojenie", con);
            new Guard(con);
          } 
          return con; 
        } catch(Exception e) {return null;}     
      }
    

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	doPost(request, response);
	}

    ///// POST /////
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		dajSpojenie(request);
    	response.setContentType("text/html;charset=UTF-8");
    	out.println("<head>");
    	out.println("<link rel='stylesheet' type='text/css' href='styles.css'>");
    	out.println("</head>");
    	try {
    		
            String operacia = request.getParameter("operacia");
            if (badConnection(out)) return;
            
            //kontrola id
            int user_id = getUserID(request);
            if (user_id == 0) { zobrazNeopravnenyPristup(out); return; }
            
            //operacie
            if(operacia != null) {
            if (operacia.equals("admin") && overAdmina(request) == -1) {  zobrazNeopravnenyPristup(out); return; }
            if (operacia.equals("nastav_pouzivatela") && nastav_pouzivatela(request) == -1) { zobrazNeopravnenyPristup(out); return; }
            if (operacia.equals("ulozit")) { ulozit(out, request); }
            if (operacia.equals("odstranit")) { odstranit(out, request);};
            }
            
            //vypis stranky
            vypisHeader(out, request);
            vypisBody(user_id, out, request);
            createFooter(out, request);
            
        } catch (Exception e) {  out.println(e); }
	}
    
    // UPDATE STAV
 	private void ulozit(PrintWriter out, HttpServletRequest request) {
 		 try {
 		        Statement stmt = con.createStatement();	        
 		        String sql = "UPDATE obj_zoznam SET stav = '" + request.getParameter("stav_objednavky") + "' WHERE ID = " + request.getParameter("id");  
 		        stmt.executeUpdate(sql);
 		        out.println("Stav položky úspešne zmenení !"); 
 		       
 		    } catch (Exception e) {
 		        out.println(e);
 		    }
 	 }
 	
 	protected int nastav_pouzivatela(HttpServletRequest request) {
		HttpSession session = request.getSession();
		session.setAttribute("admin_status", "false");
		if(((int)session.getAttribute("je_admin") == 1)) {
			return 1;
		}
		return -1;
	}
 	 // UPDATE STAV
 	private void odstranit(PrintWriter out, HttpServletRequest request) {
 		 try {
 		        Statement stmt = con.createStatement();	         
 		        String sql = "DELETE FROM obj_zoznam WHERE ID = " + request.getParameter("id");
 		        stmt.executeUpdate(sql);
 		        sql = "DELETE FROM obj_polozky WHERE ID_objednavky = " + request.getParameter("id");
		        stmt.executeUpdate(sql);
 		        out.println("Objednavka bola odstranena."); 
 		       
 		    } catch (Exception e) {
 		        out.println(e);
 		    }
 	 }

	
    /////////////////////////////
    private boolean badConnection(PrintWriter out) {
        if (errorMessage.length() > 0) {
            out.println(errorMessage);
            return true;
        }
        return false;
    }


	protected void zobrazNeopravnenyPristup(PrintWriter out) {
		out.println("Neopravneny pristup");
	}
	/////////////////////////////
	
	//// BODY ////
	protected void vypisBody(int user_id, PrintWriter out, HttpServletRequest request) {
		HttpSession session = request.getSession();
		String sql;
		
		try {
		Statement stmt= con.createStatement();
		if(session.getAttribute("admin_status") != null && session.getAttribute("admin_status").equals("true")) {
			   sql = "SELECT * FROM obj_zoznam " +
					 "INNER JOIN obj_polozky ON obj_polozky.ID_objednavky = obj_zoznam.ID " + 
					 "INNER JOIN sklad ON sklad.ID = obj_polozky.ID_tovaru ";
		}else {
			   sql = "SELECT * FROM obj_zoznam " +
					 "INNER JOIN obj_polozky ON obj_polozky.ID_objednavky = obj_zoznam.ID " + 
					 "INNER JOIN sklad ON sklad.ID = obj_polozky.ID_tovaru "+
					 "WHERE ID_pouzivatela = " + user_id;
		}
		
		ResultSet rs = stmt.executeQuery(sql);
		
		int counter = 0;
		
		while(rs.next()) {
			if(session.getAttribute("admin_status") != null && session.getAttribute("admin_status").equals("true")) {
				if(rs.getInt("obj_zoznam.ID") != counter) {
					out.println("<hr>");
					counter = rs.getInt("obj_zoznam.ID");
					out.print("&nbsp;&nbsp; <strong>Čislo objednavky: "+rs.getInt("obj_zoznam.ID")+
	                        "&nbsp;&nbsp; Datum:"+ rs.getString("obj_zoznam.datum_objednavky")+"&nbsp;&nbsp; suma: " + rs.getInt("obj_zoznam.suma") 
	                        + " EUR&nbsp;&nbsp; stav: " + rs.getString("stav"));
					
							out.println("<form action='ObjednavkyServlet' method='post'>"); 
	                        out.println("Zmeniť stav: <select name='stav_objednavky'>");
							out.println("<option value='spracovávanie'>spracovávanie</option>");
							out.println("<option value='odosielane'>odosielanie</option>");
							out.println("<option value='zaplatene'>zaplatene</option>");
	    	                out.println("</select>");
	    	                out.println("<input type='hidden' name='id' value='" + rs.getString("obj_zoznam.ID") + "'>");
	    	                out.println("<input type='hidden' name='operacia' value='ulozit'>");
	    	                out.println("<input type='submit' value='uložiť'>");
	    	                out.println("</form>");
	    	                
	    	                out.println("<form action='ObjednavkyServlet' method='post'>"); 
	    	                out.println("<input type='hidden' name='id' value='" + rs.getString("obj_zoznam.ID") + "'>");
	    	                out.println("<input type='hidden' name='operacia' value='odstranit'>");
	    	                out.println("<input type='submit' value='odstraniť objednávku'>");
	    	                out.println("</form>");
	    	                
	    	                out.println("</strong>");
					out.println("&nbsp;&nbsp; Nazov produktu: " + rs.getString("sklad.nazov") + "&nbsp;&nbsp; Pocet kusov: " + rs.getString("obj_polozky.ks") + 
								"&nbsp;&nbsp; Cena za kus: " + rs.getString("sklad.cena") + "&nbsp;&nbsp; Celkova Suma: " + (rs.getInt("sklad.cena") +  rs.getInt("obj_polozky.ks")) + "</br>");
				}else {
					out.println("&nbsp;&nbsp; Nazov produktu: " + rs.getString("sklad.nazov") + "&nbsp;&nbsp; Pocet kusov: " + rs.getString("obj_polozky.ks") + 
								"&nbsp;&nbsp; Cena za kus: " + rs.getString("sklad.cena") + "&nbsp;&nbsp; Celkova Suma: " + (rs.getInt("obj_polozky.cena") +  rs.getInt("obj_polozky.ks")) + "</br>");
					}
			}else {
			if(rs.getInt("obj_zoznam.ID") != counter) {
				out.println("<hr>");
				counter = rs.getInt("obj_zoznam.ID");
				out.println("&nbsp;&nbsp; <strong>Čislo objednavky: "+rs.getInt("obj_zoznam.ID")+
                        "&nbsp;&nbsp; Datum:"+ rs.getString("obj_zoznam.datum_objednavky")+"&nbsp;&nbsp; suma: " + rs.getInt("obj_zoznam.suma") + " EUR&nbsp;&nbsp; stav: " + rs.getString("stav") + " </strong><br>");
				out.println("&nbsp;&nbsp; Nazov produktu: " + rs.getString("sklad.nazov") + "&nbsp;&nbsp; Pocet kusov: " + rs.getString("obj_polozky.ks") + 
							"&nbsp;&nbsp; Cena za kus: " + rs.getString("sklad.cena") + "&nbsp;&nbsp; Celkova Suma: " + (rs.getInt("sklad.cena") +  rs.getInt("obj_polozky.ks")) + "</br>");
			}else {
				out.println("&nbsp;&nbsp; Nazov produktu: " + rs.getString("sklad.nazov") + "&nbsp;&nbsp; Pocet kusov: " + rs.getString("obj_polozky.ks") + 
							"&nbsp;&nbsp; Cena za kus: " + rs.getString("sklad.cena") + "&nbsp;&nbsp; Celkova Suma: " + (rs.getInt("obj_polozky.cena") +  rs.getInt("obj_polozky.ks")) + "</br>");
				}
				}
			}
		out.println("<hr>");
			rs.close();
			stmt.close();
		}catch (Exception ex) {
			out.println(ex.getMessage());
		}
	}
	
	//// FOOTER ////
	private void createFooter(PrintWriter out, HttpServletRequest request) {
		  out.println("<a href='ObjednavkyServlet'>Zoznam objednávok</a>");
		}  
		
	protected void overUser(PrintWriter out, HttpServletRequest request) {
		try {
			String login = request.getParameter("login");
			String heslo = request.getParameter("pwd");
			Statement stmt = con.createStatement();
			String sql = "SELECT MAX(id) AS iid, COUNT(id) AS pocet FROM users WHERE login='"+login+"' AND passwd = '" + heslo + "'";
			ResultSet rs = stmt.executeQuery(sql);
			rs.next();
			HttpSession session = request.getSession();
			if(rs.getInt("pocet") == 1) {
				sql = "SELECT * FROM users WHERE login = '"+login+"'";
				rs = stmt.executeQuery(sql);
				rs.next();
				session.setAttribute("ID", rs.getInt("ID"));
				session.setAttribute("login", rs.getString("login"));
				session.setAttribute("meno", rs.getString("meno"));
				session.setAttribute("priezvisko", rs.getString("priezvisko"));
				session.setAttribute("adresa", rs.getString("adresa"));
				session.setAttribute("zlava", rs.getInt("zlava"));
				session.setAttribute("je_admin", rs.getInt("je_admin"));
			} else {
				out.println("Autorizacia sa nepodarila. Skontroluj prihlasovacie udaje.");
				session.invalidate();
			}
			rs.close();
			stmt.close();
		}catch(Exception e) {out.println(e.getMessage());} 
	}
	
	protected int getUserID(HttpServletRequest request) {
		HttpSession session = request.getSession();
		Integer id = (Integer)(session.getAttribute("ID"));
		if(id==null) id = 0;
		return id;  
	}
	
	protected int overAdmina(HttpServletRequest request) {
		HttpSession session = request.getSession();
		session.setAttribute("admin_status", "true");
		if(((int)session.getAttribute("je_admin") == 1)) {
			return 1;
		}
		return -1;
	}
	
	protected void urobLogout(PrintWriter out, HttpServletRequest request) {
		HttpSession session = request.getSession();
		session.invalidate();
		out.println("Odhlasenie bolo uspešné.<br>");
		out.println("<a href='index.html'>prihlasiť sa</a>");
	}
	
	////HEADER ////
	protected void vypisHeader(PrintWriter out, HttpServletRequest request) {
		HttpSession session = request.getSession();
		// Admin tlacidla
		if (((int) session.getAttribute("je_admin")) == 1) {
			out.println("<div class='admin-buttons'>");

			out.println("<div class='admin-form-wrapper'>");
			out.println("<form method='post' action='ObjednavkyServlet'>");
			out.println("<input type='hidden' name='operacia' value='nastav_pouzivatela'>");
			out.println("<input class='admin-styled-button' type='submit' value='Pouzivatelske rozhranie'>");
			out.println("</form>");
			out.println("</div>");

			out.println("<div class='admin-form-wrapper'>");
			out.println("<form method='post' action='ObjednavkyServlet'>");
			out.println("<input type='hidden' name='operacia' value='admin'>");
			out.println("<input class='admin-styled-button' type='submit' value='Adminstrátorské rozhranie'>");
			out.println("</form>");
			out.println("</div>");

			out.println("</div>");

		}

		// kosik
		String vypis = (String) session.getAttribute("meno") + " " + (String) session.getAttribute("priezvisko");
		out.println("<div class='user-header'>");
		out.println("<p>" + vypis + "</p>");
		out.println("<a class='cart-link' href='KosikServlet'>Košik</a>");

		// logout
		out.println("<form method='post' action='MainServlet'>");
		out.println("<input type='hidden' name='operacia' value='logout'>");
		out.println("<input class='logout-button' type='submit' value='logout'>");
		out.println("</form>");
		
		//pokracovat v nakupe
		out.println("<div class='continue-shopping'>");
		out.println("<form method='post' action='MainServlet'>");
		out.println("<input type='hidden' name='operacia' value='naspat'>");
		out.println("<input type='submit' class='continue-shopping-button' value='Pokračovať v nákupe'>");
		out.println("</form>");
		out.println("</div>");

		out.println("</div>");

	}
}
