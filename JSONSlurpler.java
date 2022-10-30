//READING THE JSON AND UPDATING THE VALUES IN JSON 

import java.text.SimpleDateFormat;
import java.io.IOException;
//import java.time.*
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
 
Date now = new Date()

SimpleDateFormat timestamp = new SimpleDateFormat("yyyyMMddHHmmss");

job."Timestamp" = timestamp.format(now);



//println(job."_JSONBase64")
byte[] decoded = job."_JSONBase64".decodeBase64()
String DecodedJSON = new String(decoded);
println("decstr="+DecodedJSON)

//def map = DecodedJSON.toString()
def slurped = new JsonSlurper().parseText(DecodedJSON)
//println("dent"+slurped.attributes[0].value)
slurped.attributes[5].value = now.format("YYYY-MM-dd")// hh:mm:ss", TimeZone.getTimeZone('AEDT'))
slurped.attributes[6].value = job.'gmc-businessObject-historyLast-holder-user-userName'
//slurped.attributes[0].value = job.'gmc-businessObject-historyLast-holder-user-userName'
//println("sent"+slurped.attributes[0].value)
def builder = new JsonBuilder(slurped)
//builder.content.attributes[0].value = job.'gmc-businessObject-historyLast-holder-user-userName'  
//builder.content.ApproverDate = now.format("YYYY-MM-DD hh:mm:ss", TimeZone.getTimeZone('AEDT'))    
String temp = builder.toString()

 temp = temp.replaceAll(/:/ ,/: / )
temp = temp.replaceAll("attributes\": " ,"attributes\":" )
println("testsluper="+ temp)
job.setAttribute("_JSON",temp)
//To set error code to T500(Error during rendering to PDF)
//Start of generic error handling para. This needs to be called for each script.

String[] category = new String[50];
String line;
String JobID,CorrelationID;
String JobCode,JobStatus,JobDesc,TicketCode,TicketDesc,TicketID,TicketURL,returncode;

File file = new File("D:/Quadient/InspireAutomation/spool/slot" + job."job-spool-slot" + "/" + job.'job-identifier' + "/ErrorCodes.txt");
FileReader fileReader = new FileReader(file);
BufferedReader bufferedReader = new BufferedReader(fileReader);
StringBuffer stringBuffer = new StringBuffer();	
int i = 0;	
while ((line = bufferedReader.readLine()) != null) {	
		category[i] = line;
		i++;
}
fileReader.close();

JobID  = job.getAttribute("job-identifier");
CorrelationID  = job.getAttribute("CorrelationID");
returncode = "T500";
job."Error_Response" = Response.Test1.ResponseCreation(category,returncode,JobID,CorrelationID,TicketID,TicketURL);

