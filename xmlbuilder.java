//BUILDING UP THE XML AND WRITING TO A FILE

import java.io.*;
import groovy.xml.MarkupBuilder;

def writer = new StringWriter()
def xml = new MarkupBuilder(writer)	 
	xml.ReferenceData()
	{ 
		BatchName(job.'BatchName')
		FromName(job.'FromName')
		FromMail(job.'FromEmailAddress')
		ReplyName(job.'ReplyToName')
		ReplyMail(job.'ReplyToEmailAddress')
		Attachments()
		{
			Attachments()
		}
		AttachmentNames()
		{
			AttachmentNames()
		}
		Envelope(job.'Envelope')
		Inserts(job.'InsertCode')//confirm 
		spoolLocation()//confirm
		SMSVirtualNumber(job.'SMSVirtualNumber')
		Website(job.'Website')
		DocumentType()
		AddressLine2(job.'addressLine2')
		OpeningHours(job.'openingHourText')
	}
	println(writer.toString())
	
def dbinput = writer.toString()

def dbticketdata = new ArrayList()
dbticketdata = dbinput.bytes.encodeBase64().toString();

defineVariable("dbInputdata", dbticketdata);

def input = new File ( "D:/Quadient/InspireAutomation/spool/slot" + job."job-spool-slot" + "/" + job.'job-identifier' + "/Input.xml" )

def ticketdata = new ArrayList()
ticketdata = input.text.bytes.encodeBase64().toString();

defineVariable("Inputdata", ticketdata);
