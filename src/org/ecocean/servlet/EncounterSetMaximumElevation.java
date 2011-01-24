package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;


public class EncounterSetMaximumElevation extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Encounter enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean isOwner = true;
    String newElev = "unknown";

    /**
     if(request.getParameter("number")!=null){
     myShepherd.beginDBTransaction();
     if(myShepherd.isEncounter(request.getParameter("number"))) {
     Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
     String locCode=verifyMyOwner.getLocationCode();

     //check if the encounter is assigned
     if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
     isOwner=true;
     }

     //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
     else if((request.isUserInRole("admin"))){
     isOwner=true;
     }
     //if they have general location code permissions for the encounter's location code
     else if(request.isUserInRole(locCode)){isOwner=true;}
     }
     myShepherd.rollbackDBTransaction();
     }
     */

    //reset encounter elevation in meters

    if ((request.getParameter("number") != null) && (request.getParameter("elevation") != null) && (!request.getParameter("elevation").equals(""))) {
      myShepherd.beginDBTransaction();
      Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(changeMe);
      double oldElev = -1;


      try {
        oldElev = changeMe.getMaximumElevationInMeters();

        double elevation = (new Double(request.getParameter("elevation"))).doubleValue();

        changeMe.setMaximumElevationInMeters(elevation);


        if (elevation > -99999) {
          newElev = Double.toString(elevation);
        }

        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed reported elevation from " + oldElev + " meters to " + newElev + " meters.</p>");
      } catch (NumberFormatException nfe) {
        System.out.println("Bad numeric input on attempt to change elevation for the encounter.");
        locked = true;
        nfe.printStackTrace();
        myShepherd.rollbackDBTransaction();
      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader());
        out.println("<strong>Success:</strong> Encounter elevation has been updated from " + oldElev + " meters to " + newElev + " meters.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a></p>\n");
        out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
        out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
        out.println(ServletUtilities.getFooter());
        String message = "The elevation of encounter " + request.getParameter("number") + " has been updated from " + oldElev + " meters to " + newElev + " meters.";
        ServletUtilities.informInterestedParties(request.getParameter("number"), message);
      } else {
        out.println(ServletUtilities.getHeader());
        out.println("<strong>Failure:</strong> Encounter elevation was NOT updated because another user is currently modifying the record for this encounter, or the value input does not translate to a valid elevation number.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a></p>\n");
        out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
        out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
        out.println(ServletUtilities.getFooter());


      }
    } else {
      out.println(ServletUtilities.getHeader());
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
      out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      out.println(ServletUtilities.getFooter());

    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}
  
  
