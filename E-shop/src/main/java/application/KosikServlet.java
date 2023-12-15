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
import java.util.ArrayList;

public class KosikServlet extends HttpServlet {
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
            else if (operacia.equals("odstran_polozku")) { odobratPolozku(user_id, out, request); }
            else if (operacia.equals("objednat")) { objednat(user_id, out, request); }
            }
            
            //vypis stranky
            vypisHeader(out, request);
            vypisBody(out, request);
            createFooter(out, request);
            
        } catch (Exception e) {  out.println(e); }
	}
    
    protected int nastav_pouzivatela(HttpServletRequest request) {
		HttpSession session = request.getSession();
		session.setAttribute("admin_status", "false");
		if(((int)session.getAttribute("je_admin") == 1)) {
			return 1;
		}
		return -1;
	}

    // OBJEDNANIE TOVARU S KOSIKU
    private synchronized void objednat(int id_user, PrintWriter out, HttpServletRequest request) {
        try {
            Statement stmt = con.createStatement();
            String check_db = "SELECT * FROM sklad "
            		+ "INNER JOIN kosik ON kosik.ID_tovaru = sklad.ID "
            		+ "WHERE " + id_user + " = kosik.ID_pouzivatela";
            
			ResultSet sql = stmt.executeQuery(check_db);
			while(sql.next()) {
				if(sql.getInt("sklad.ks") < sql.getInt("kosik.ks")) {
					out.println("Na sklade nieje dostatok tovaru: " + sql.getString("sklad.nazov"));
					return;
				}
			}
			
			String sql_kosik = "SELECT * FROM kosik WHERE " + id_user + " = ID_pouzivatela";
			sql = stmt.executeQuery(sql_kosik);

			ArrayList<String> arr = new ArrayList<>();	
			
			while(sql.next()) {
				arr.add("UPDATE sklad SET ks = ks - " + sql.getInt("ks") + " WHERE " +
			    		   "(ID = "+ sql.getInt("ID_tovaru") +" )");
			}
			
			for(String s: arr) {
				stmt.executeUpdate(s);
			}

            String sql_count = "SELECT COUNT(*) AS objednavka FROM obj_zoznam WHERE " + id_user + " = ID_pouzivatela";
            sql = stmt.executeQuery(sql_count);
            sql.next();
			int poradie_obj = sql.getInt("objednavka") + 1;
			
            String sql_zoznam = "INSERT INTO obj_zoznam (ID_pouzivatela, suma, stav, obj_cislo, datum_objednavky) " +
                                      "SELECT " + id_user + ", SUM(cena * ks), 'spracovava', " + poradie_obj +
                                      ", NOW() FROM kosik " +
                                      "WHERE ID_pouzivatela = " + id_user;
            stmt.executeUpdate(sql_zoznam);
            
            String sql2 = "SELECT ID FROM obj_zoznam WHERE " + id_user + " = ID_pouzivatela AND obj_cislo = " + poradie_obj;
            sql = stmt.executeQuery(sql2);
            sql.next();

            String obj_polozky = "INSERT INTO obj_polozky (ID_objednavky, ID_tovaru, cena, ks) " +
                                      "SELECT " + sql.getString("ID") + ", ID_tovaru, cena, ks " +
                                      "FROM kosik ";

            stmt.executeUpdate(obj_polozky);
            
            String sql_delete = "DELETE FROM `kosik` WHERE ID_pouzivatela = " + id_user;
	    	stmt.executeUpdate(sql_delete);
	    	  
	    	out.println("Uspešne si objednal tovar!");

	    	sql.close();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
            out.println(e.getMessage());
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
	protected void vypisBody(PrintWriter out, HttpServletRequest request) {
	HttpSession session = request.getSession();
	Integer zlava = (Integer)session.getAttribute("zlava");
	double aktCena = 0;  
	  
	try {
	Statement stmt= con.createStatement();
	String sql = "SELECT * FROM kosik " +
				 "INNER JOIN sklad ON kosik.ID_tovaru = sklad.ID "
				 + "WHERE ID_pouzivatela = " + session.getAttribute("ID");
	ResultSet rs = stmt.executeQuery(sql);
	out.println("<hr>");
	while(rs.next()) {
		aktCena = rs.getDouble("sklad.cena")*(100-zlava)/100;
		out.println("<form class='product-form' action='KosikServlet' method='post'>");
	    out.println("<input type='hidden' name='ID' value= '" +
	                 rs.getString("kosik.ID_tovaru")+"'>");
	    out.println("<input type='hidden' name='cena' value='"+
                aktCena+"'>");
	    out.println("<input type='hidden' name='operacia' value='odstran_polozku'>");
	    out.println("<input type='submit' class='buy-button' value='odobrat s košika'>");	
	    out.println("&nbsp;&nbsp;<strong class='product-name'>"+rs.getString("nazov")+
	                            ":</strong>"+ rs.getString("cena")+" EUR " +
	                            rs.getString("ks") + " pocet kusov");
	    // Vypis obrazku
	    if (rs.getString("nazov").equals("vianočný kapor")) {
	        out.println("<img class='product-image' src='images/kapor.png' alt='Kapor' width='50' height='50'>");
	    } else if (rs.getString("nazov").equals("nohavice")) {
	        out.println("<img class='product-image' src='images/nohavice.jpg' alt='nohavice' width='50' height='50'>");
	    } else if (rs.getString("nazov").equals("ponožky")) {
	        out.println("<img class='product-image' src='images/ponozky.jpg' alt='jogurt' width='50' height='50'>");
	    } else if (rs.getString("nazov").equals("tvarohový jogurt")) {
	        out.println("<img class='product-image' src='images/jogurt.jpg' alt='Kapor' width='50' height='50'>");
	    }
	    
	    out.println("</form><hr>");
		}
		rs.close();
		stmt.close();
	}catch (Exception ex) {
		out.println(ex.getMessage());
	}
	//objednat
	out.println("<form method='post' action='KosikServlet'>");
	out.println("<input type='hidden' name='operacia' value='objednat'>");
	out.println("<input type='submit' value='objednat'>");
	out.println("</form></div>");	
	}
	
	//metoda na odobranie polozky z kosiku
	private void odobratPolozku(int id_user, PrintWriter out, HttpServletRequest request) {
		  int id_tovar = Integer.parseInt(request.getParameter("ID"));  
		  double cena = Double.parseDouble(request.getParameter("cena"));
		  try {
		    Statement stmt = con.createStatement();
		    String sql = "SELECT ks FROM kosik WHERE " 
		          + "(ID_pouzivatela="+id_user+") AND (id_tovaru =" 
		          +id_tovar+")";
		    ResultSet rs = stmt.executeQuery(sql);
		    rs.next();
		    int pocet = rs.getInt("ks");
		    if (pocet == 1) {
		    	  sql = "DELETE FROM `kosik` WHERE id_tovaru = " + id_tovar + " AND ID_pouzivatela = " + id_user;
		    	  stmt.executeUpdate(sql);
		    } else {
		    	 sql = "UPDATE kosik SET ks = ks - 1, cena = "+cena+" WHERE "+
		    		   "(ID_pouzivatela= "+id_user+") AND (id_tovaru ="+ id_tovar+")";
		    	stmt.executeUpdate(sql);
		    } 
		    rs.close();
		    stmt.close();
		  } catch (Exception e) {}
	}
	//// FOOTER ////
	private void createFooter(PrintWriter out, HttpServletRequest request) {
		out.println("<a class='footer-link' href='ObjednavkyServlet'>Zoznam objednávok</a>");
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
			out.println("<form method='post' action='MainServlet'>");
			out.println("<input type='hidden' name='operacia' value='nastav_pouzivatela'>");
			out.println("<input class='admin-styled-button' type='submit' value='Pouzivatelske rozhranie'>");
			out.println("</form>");
			out.println("</div>");

			out.println("<div class='admin-form-wrapper'>");
			out.println("<form method='post' action='MainServlet'>");
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
