import java.io.*;
import groovy.xml.MarkupBuilder;
import java.util.*;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.io.File;
import groovy.sql.Sql;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import groovy.xml.XmlUtil
import groovy.io.FileType;


def db = [url:server.'Database_URL', user:server.'Database_User', password:server.'Database_Password', driver:server.'Database_Driver']
def sql = Sql.newInstance(db.url, db.user, db.password, db.driver)

def now = new Date()
def approverDate = now.format("yyyy-MM-dd'T'HH:mm:ss")

def strCommunicationID = job."CommunicationID"

sql.execute("""
			UPDATE [dbo].[Communication]
				 SET [DispatchDate] = '${approverDate}'
			WHERE CommunicationID = '${strCommunicationID}'
			""");