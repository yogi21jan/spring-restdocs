/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.restdocs.payload;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.restdocs.snippet.SnippetException;
import org.springframework.restdocs.snippet.TemplatedSnippet;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * A {@link TemplatedSnippet} that produces a snippet documenting a RESTful resource's
 * request or response fields.
 *
 * @author Andreas Evers
 * @author Andy Wilkinson
 */
public abstract class AbstractFieldsSnippet extends TemplatedSnippet {

	private List<FieldDescriptor> fieldDescriptors;

	AbstractFieldsSnippet(String type, Map<String, Object> attributes,
			List<FieldDescriptor> descriptors) {
		super(type + "-fields", attributes);
		for (FieldDescriptor descriptor : descriptors) {
			Assert.notNull(descriptor.getPath());
			Assert.hasText(descriptor.getDescription());
		}
		this.fieldDescriptors = descriptors;
	}

	@Override
	protected Map<String, Object> document(MvcResult result) throws IOException {
		MediaType contentType = getContentType(result);
		PayloadHandler payloadHandler;
		if (contentType != null
				&& MediaType.APPLICATION_XML.isCompatibleWith(contentType)) {
			payloadHandler = new XmlPayloadHandler(readPayload(result));
		}
		else {
			payloadHandler = new JsonPayloadHandler(readPayload(result));
		}

		validateFieldDocumentation(payloadHandler);

		for (FieldDescriptor descriptor : this.fieldDescriptors) {
			if (descriptor.getType() == null) {
				descriptor.type(payloadHandler.determineFieldType(descriptor.getPath()));
			}
		}

		Map<String, Object> model = new HashMap<>();
		List<Map<String, Object>> fields = new ArrayList<>();
		model.put("fields", fields);
		for (FieldDescriptor descriptor : this.fieldDescriptors) {
			fields.add(descriptor.toModel());
		}
		return model;
	}

	private String readPayload(MvcResult result) throws IOException {
		StringWriter writer = new StringWriter();
		FileCopyUtils.copy(getPayloadReader(result), writer);
		return writer.toString();
	}

	private void validateFieldDocumentation(PayloadHandler payloadHandler) {
		List<FieldDescriptor> missingFields = payloadHandler
				.findMissingFields(this.fieldDescriptors);
		String undocumentedPayload = payloadHandler
				.getUndocumentedPayload(this.fieldDescriptors);

		if (!missingFields.isEmpty() || StringUtils.hasText(undocumentedPayload)) {
			String message = "";
			if (StringUtils.hasText(undocumentedPayload)) {
				message += String.format("The following parts of the payload were"
						+ " not documented:%n%s", undocumentedPayload);
			}
			if (!missingFields.isEmpty()) {
				if (message.length() > 0) {
					message += String.format("%n");
				}
				List<String> paths = new ArrayList<String>();
				for (FieldDescriptor fieldDescriptor : missingFields) {
					paths.add(fieldDescriptor.getPath());
				}
				message += "Fields with the following paths were not found in the"
						+ " payload: " + paths;
			}
			throw new SnippetException(message);
		}
	}

	protected abstract MediaType getContentType(MvcResult result);

	protected abstract Reader getPayloadReader(MvcResult result) throws IOException;

}