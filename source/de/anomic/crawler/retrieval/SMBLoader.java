// SMBLoader.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.03.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based search engine
//
// $LastChangedDate: 2010-03-07 00:41:51 +0100 (So, 07 Mrz 2010) $
// $LastChangedRevision: 6719 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


package de.anomic.crawler.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseHeader;
import de.anomic.net.ftpc;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.data.MimeTable;

import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.kelondro.util.FileUtils;

public class SMBLoader {

    private final Switchboard sb;
    private final Log log;
    private final int maxFileSize;

    public SMBLoader(final Switchboard sb, final Log log) {
        this.sb = sb;
        this.log = log;
        maxFileSize = (int) sb.getConfigLong("crawler.smb.maxFileSize", -1l);
    }
    
    
    public Response load(final Request request, boolean acceptOnlyParseable) throws IOException {
        DigestURI url = request.url();
        if (!url.getProtocol().equals("smb")) throw new IOException("wrong loader for SMBLoader: " + url.getProtocol());

        RequestHeader requestHeader = new RequestHeader();
        if (request.referrerhash() != null) {
            DigestURI ur = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
            if (ur != null) requestHeader.put(RequestHeader.REFERER, ur.toNormalform(true, false));
        }
        
        // process directories: transform them to html with meta robots=noindex (using the ftpc lib)
        if (url.isDirectory()) {
            List<String> list = new ArrayList<String>();
            String u = url.toNormalform(true, true);
            String[] l = url.list();
            if (l == null) {
                // this can only happen if there is no connection or the directory does not exist
                log.logInfo("directory listing not available. URL = " + request.url().toString());
                sb.crawlQueues.errorURL.push(request, this.sb.peers.mySeed().hash.getBytes(), new Date(), 1, "directory listing not available. URL = " + request.url().toString());
                throw new IOException("directory listing not available. URL = " + request.url().toString());
            }
            for (String s: l) list.add(u + s);
         
            StringBuilder content = ftpc.dirhtml(u, null, null, null, list, true);
            
            ResponseHeader responseHeader = new ResponseHeader();
            responseHeader.put(HeaderFramework.LAST_MODIFIED, DateFormatter.formatRFC1123(new Date()));
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
            Response response = new Response(
                    request, 
                    requestHeader,
                    responseHeader,
                    "200",
                    sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle()),
                    content.toString().getBytes());
            
            return response;
        }
        
        // create response header
        String mime = MimeTable.ext2mime(url.getFileExtension());
        ResponseHeader responseHeader = new ResponseHeader();
        responseHeader.put(HeaderFramework.LAST_MODIFIED, DateFormatter.formatRFC1123(new Date(url.lastModified())));
        responseHeader.put(HeaderFramework.CONTENT_TYPE, mime);
        
        // check mime type and availability of parsers
        // and also check resource size and limitation of the size
        long size = url.length();
        String parserError = null;
        if ((acceptOnlyParseable && (parserError = TextParser.supports(url, mime)) != null) ||
            (size > maxFileSize && maxFileSize >= 0)) {
            // we know that we cannot process that file before loading
            // only the metadata is returned
            
            if (parserError != null) {
                log.logInfo("No parser available in SMB crawler: '" + parserError + "' for URL " + request.url().toString() + ": parsing only metadata");
            } else {
                log.logInfo("Too big file in SMB crawler with size = " + size + " Bytes for URL " + request.url().toString() + ": parsing only metadata");
            }
            
            // create response with metadata only
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/plain");
            Response response = new Response(
                    request, 
                    requestHeader,
                    responseHeader,
                    "200",
                    sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle()),
                    url.toNormalform(true, true).getBytes());
            return response;
        }
        
        // load the resource
        InputStream is = url.getInputStream();
        byte[] b = FileUtils.read(is);
        is.close();
        
        // create response with loaded content
        Response response = new Response(
                request, 
                requestHeader,
                responseHeader,
                "200",
                sb.crawler.profilesActiveCrawls.getEntry(request.profileHandle()),
                b);
        return response;
    }
    
}
