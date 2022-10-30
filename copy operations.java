import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import groovy.xml.XmlUtil;
import java.io.File;
import groovy.json.*
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import groovy.json.JsonBuilder;

//Start of generic error handling para. This needs to be called for each script.
String[] category = new String[50];
String line;

String JobID,CorrelationID,RequestType,UserName,ChannelPref,EmailId,City,State,PostCode,Country,TemplateID,TemplateIDcount,Address,Street1,Street2,Street3;;
String JobCode,JobStatus,JobDesc,TicketCode,TicketDesc,TicketID,TicketURL,returncode;

String DestChannelLocation = server.'crjob_spool_location' + "/slot" + job.'job-spool-slot' + "/" + job.'job-identifier' + "/" + job."crDeliveryChannel_UC"+"/"+document.'crResend_AttachmentName'+ "_" + job.'crCorrelationID' + ".pdf";

String DestcrLocation = server.'crjob_spool_location' + "/slot" + job.'job-spool-slot' + "/" + job.'job-identifier' + "/cr" + "/"+document.'crResend_AttachmentName'+ "_" + job.'crCorrelationID' + ".pdf";

File file = new File(server.'crjob_spool_location' + "/slot" + job."job-spool-slot" + "/" + job.'job-identifier' + "/ErrorCodes.txt");
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
RequestType  = job.getAttribute("crJobtype");
CorrelationID  = job.getAttribute("crCorrelationID");

String width,height;
def intWidth, intHeight;
def rWidth, rHeight;
def isA4Page;

File pageSizefile = new File(server.'crjob_spool_location' + "/slot" + job."job-spool-slot" + "/" + job.'job-identifier' + "/" + document."crResend_AttachmentName"+ ".xml" );

def pageSizeData = new XmlSlurper().parse(pageSizefile)

width = pageSizeData.PageSizeStatistics.Width.toString();
rWidth = 1000*(width.toDouble());
intWidth =  rWidth.round();

println("width:"+intWidth);

height = pageSizeData.PageSizeStatistics.Height.toString();
rHeight = 1000*(height.toDouble());
intHeight =  rHeight.round();
println("height:"+intHeight);

if ( (intWidth ==210 && intHeight == 297) || (intWidth == 297 && intHeight == 210) ){
	if((document.crResend_watermark == "") || (document.'crResend_watermark' == null)){

		//Move the downloaded pdf without any change to output Folder
		String presentLocation = server.'crjob_spool_location' + "/slot" + job.'job-spool-slot' + "/" + job.'job-identifier' + "/" +document.'crResend_AttachmentName'+".pdf";
		println("present:"+presentLocation);
		String newLocation = server.'crjob_spool_location' + "/slot" + job.'job-spool-slot' + "/" + job.'job-identifier' + "/" + "Attachments"+"/" +document.'crResend_AttachmentName'+ "_" + job.'crCorrelationID' + ".pdf";
		println("New:"+newLocation);
		(new AntBuilder()).copy(file: presentLocation, tofile: newLocation);
		(new AntBuilder()).copy(file: presentLocation, tofile: DestChannelLocation);
		(new AntBuilder()).copy(file: presentLocation, tofile: DestcrLocation);	
		isA4Page = false;
	}
	else{	
		isA4Page = true;
		println("A4Page:"+isA4Page);
	}
}
else 
{
isA4Page = false;

println("A4Page:"+isA4Page); 
}

document."crPagesize_IsA4" = isA4Page;


