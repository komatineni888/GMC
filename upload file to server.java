import java.nio.charset.*
import groovy.json.JsonBuilder
import groovy.io.FileType
import java.io.IOException;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import groovy.xml.XmlUtil;
import groovy.sql.Sql;
import java.text.SimpleDateFormat;
import org.apache.pdfbox.pdmodel.PDDocument

URL url = new URL(server.getAttribute("UploadAPIIndividual"));
String boundary = UUID.randomUUID().toString();

// These variables will have to be dynamically extracted for each job
String fileDir = server.'job_spool_location'+"/slot"+job."job-spool-slot"+"/"+job."job-identifier"+"/" ;
String initialSystem = "GMC";
String setexternalIdSource = "";
String setexternalIdValue = boundary;


//To Extract Date/Time
TimeZone.setDefault(TimeZone.getTimeZone('AET'))
def now = new Date()
def approverDate = now.format("yyyy-MM-dd'T'HH:mm:ss");

//To extract filenames to be uploaded 
def list = []
def dir = new File(fileDir)
dir.eachFile (FileType.FILES) { file ->
  list << file.getName()
}

String attachmentcsv = server.'job_spool_location' + "/slot" + job."job-spool-slot" + "/" + job.'job-identifier' + "/Input/attachments.csv"

int i = 0;
def temp = [];
def attachmentlist = []
def tempattachmentlist1 = []
def tempattachmentlist2 = []

def attachmentFilelist = []

new File(attachmentcsv).splitEachLine("//") {fields ->
   temp[i] = fields[1]
  attachmentlist[i] =  (new File(temp[i]).getPath());
  tempattachmentlist1[i] = (new File(temp[i]).getName()).split("\\.")[0]
  tempattachmentlist2[i] = (new File(temp[i]).getName()).split("\\.")[1]
  attachmentFilelist[i] = tempattachmentlist1[i]+"_"+job."CorrelationID"+"."+tempattachmentlist2[i]
  println("OUTPUTFILENAME"+attachmentFilelist[i])
   attachmentlist[i] = "icm:S:Production:\\\\"+ attachmentlist[i]
	i++
}

int logicalgrpcount = list.size();
String EmailSubject = "";
if (job."DeliveryChannel" == "email")
{
	
// To extract Email Subject
	String MetaDataFile = server.'job_spool_location'+"/slot"+job."job-spool-slot"+"/"+job."job-identifier"+"/"+"Temp_EmailMetadata.xml" ;
	
	def xmlfile = new File(MetaDataFile)
	Documents = new XmlSlurper().parseText(xmlfile.text)
	EmailSubject = Documents.EmailSubject.text()
	println(EmailSubject)
}
	
void sendFile(OutputStream out, String name, InputStream fileStream, String filename) {
  String o = "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n\r\n";
  println(o)
  out.write(o.getBytes(StandardCharsets.UTF_8));
  o = "Content-Type: application/pdf";
  out.write(o.getBytes(StandardCharsets.UTF_8));
  byte[] buffer = new byte[2048];
  for (int n = 0; n >= 0; n = fileStream.read(buffer))
    out.write(buffer, 0, n);
  out.write("\r\n".getBytes(StandardCharsets.UTF_8));
}

def tempcntr = job."ExternalIdCounter"
int seq = tempcntr.toInteger()+1;
for (int k = 0;k < attachmentFilelist.size(); k++)
{
	// Create the HTTP connection
	
	URLConnection con = url.openConnection();
	HttpURLConnection http = (HttpURLConnection)con;
	http.setRequestMethod("POST"); 
	http.setDoOutput(true);
	
	// Create the unique boundary string
	byte[] boundaryBytes = ("--" + boundary + "\r\n").getBytes(Charset.forName("UTF-8"));
	byte[] finishBoundaryBytes = ("--" + boundary + "--").getBytes(Charset.forName("UTF-8"));
	
	// Set the HTTP Headers
	http.setRequestProperty("Content-Type","multipart/form-data; charset=UTF-8; boundary=" + boundary);
	http.setRequestProperty("X-InitialSystem", initialSystem);
	http.setRequestProperty("X-InitialComponent", "");
	http.setRequestProperty("Transfer-Encoding", "");
	http.setRequestProperty("MIME-Version", "1.0");
	// Enable streaming mode with default settings
	http.setChunkedStreamingMode(0); 
	
	// Send the metadata fields:
	OutputStream out = http.getOutputStream(); 
	// Send the header
	out.write(boundaryBytes);
	
	String filePath = fileDir + "/" + attachmentFilelist[k];
	println(filePath);
	InputStream file = new FileInputStream(filePath);
	sendFile(out, "file", file, attachmentFilelist[k]);
	
	// Send a seperator
	out.write(boundaryBytes);
	
} 

//THIS IS  UPLOAD FOR ATACHMENETS OF CREATEJOB