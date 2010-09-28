package com.plugin.etagFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.apache.log4j.Logger;

import com.cj.etag.ETagResponseWrapper;

public class EtagFilter implements Filter {
	Logger logger = Logger.getLogger(getClass());

	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest servletRequest = (HttpServletRequest) req;
		HttpServletResponse servletResponse = (HttpServletResponse) res;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ETagResponseWrapper wrappedResponse = new ETagResponseWrapper(
				servletResponse, baos);
		chain.doFilter(servletRequest, wrappedResponse);
		StrBuilder str = new StrBuilder();
		for(Cookie cookie: servletRequest.getCookies()){
			str.append(cookie.getName()).append("=").append(cookie.getValue()).append("\n");
		}
		String varyHeaders = servletRequest.getHeader("Accept") + str.toString();
		@SuppressWarnings("unchecked")
		List<Byte> byteList = ListUtils.union(Arrays.asList(baos.toByteArray()), Arrays.asList(varyHeaders.getBytes()));
		byte[] bytes = ArrayUtils.toPrimitive(byteList.toArray(new Byte[byteList.size()]));

		String token = '"' + ETagComputeUtils.getMd5Digest(bytes) + '"';
		servletResponse.setHeader("ETag", token); // always store the ETag in
													// the header

		String previousToken = servletRequest.getHeader("If-None-Match");
		if (previousToken != null && previousToken.equals(token)) { // compare
																	// previous
																	// token
																	// with
																	// current
																	// one
			logger.debug("ETag match: returning 304 Not Modified");
			servletResponse.sendError(HttpServletResponse.SC_NOT_MODIFIED);
			// use the same date we sent when we created the ETag the first time
			// through
			servletResponse.setHeader("Last-Modified",
					servletRequest.getHeader("If-Modified-Since"));
		} else { // first time through - set last modified time to now
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.MILLISECOND, 0);
			Date lastModified = cal.getTime();
			servletResponse.setDateHeader("Last-Modified",
					lastModified.getTime());

			logger.debug("Writing body content");
			servletResponse.setContentLength(bytes.length);
			ServletOutputStream sos = servletResponse.getOutputStream();
			sos.write(bytes);
			sos.flush();
			sos.close();
		}
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		// TODO Auto-generated method stub

	}
}
