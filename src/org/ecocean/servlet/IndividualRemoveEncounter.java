package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;


public class IndividualRemoveEncounter extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    /**
     if(request.getParameter("number")!=null){
     myShepherd.beginDBTransaction();
     if(myShepherd.isEncounter(request.getParameter("number"))) {
     Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
     String locCode=verifyMyOwner.getLocationCode();

     //check if the encounter is assigned
     if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
     isAssigned=true;
     }

     //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
     if((request.isUserInRole("admin"))||(isAssigned)){
     isOwner=true;
     }
     //if they have general location code permissions for the encounter's location code
     else if(request.isUserInRole(locCode)){isOwner=true;}
     }
     myShepherd.rollbackDBTransaction();
     }
     */

    //remove encounter from MarkedIndividual

    if ((request.getParameter("number") != null)) {
      myShepherd.beginDBTransaction();
      Encounter enc2remove = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2remove);
      if (!enc2remove.isAssignedToMarkedIndividual().equals("Unassigned")) {
        String old_name = enc2remove.isAssignedToMarkedIndividual();
        boolean wasRemoved = false;
        String name_s = "";
        try {
          MarkedIndividual removeFromMe = myShepherd.getMarkedIndividual(old_name);
          name_s = removeFromMe.getName();
          while (removeFromMe.getEncounters().contains(enc2remove)) {
            removeFromMe.removeEncounter(enc2remove);
          }
          while (myShepherd.getUnidentifiableEncountersForMarkedIndividual(old_name).contains(enc2remove)) {
            removeFromMe.removeEncounter(enc2remove);
          }
          enc2remove.assignToMarkedIndividual("Unassigned");
          enc2remove.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed from " + old_name + ".</p>");
          removeFromMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed encounter#" + request.getParameter("number") + ".</p>");

          if ((removeFromMe.totalEncounters() + removeFromMe.totalLogEncounters()) == 0) {
            myShepherd.throwAwayMarkedIndividual(removeFromMe);
            wasRemoved = true;
          }

        } catch (java.lang.NullPointerException npe) {
          npe.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        } catch (Exception le) {
          le.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          out.println(ServletUtilities.getHeader());
          out.println("<strong>Success:</strong> Encounter #" + request.getParameter("number") + " was successfully removed from " + old_name + ".");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
          if (wasRemoved) {
            out.println("Record <strong>" + name_s + "</strong> was also removed because it contained no encounters.");
          }
          out.println(ServletUtilities.getFooter());
          String message = "Encounter #" + request.getParameter("number") + " was removed from " + old_name + ".";
          ServletUtilities.informInterestedParties(request.getParameter("number"), message);
          if (!wasRemoved) {
            ServletUtilities.informInterestedIndividualParties(old_name, message);
          }
        } else {
          out.println(ServletUtilities.getHeader());
          out.println("<strong>Failure:</strong> Encounter #" + request.getParameter("number") + " was NOT removed from " + old_name + ". Another user is currently modifying this record entry. Please try again in a few seconds.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
          out.println(ServletUtilities.getFooter());

        }

      } else {
        myShepherd.rollbackDBTransaction();
        out.println(ServletUtilities.getHeader());
        out.println("<strong>Error:</strong> You can't remove this encounter from a marked individual because it is not assigned to one.");
        out.println(ServletUtilities.getFooter());
      }


    } else {
      out.println("I did not receive enough data to remove this encounter from a marked individual.");
    }


    out.close();
    myShepherd.closeDBTransaction();
  }

}