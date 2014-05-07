package ca.uhn.fhir.rest.method;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.TagList;
import ca.uhn.fhir.model.dstu.valueset.RestfulOperationSystemEnum;
import ca.uhn.fhir.model.dstu.valueset.RestfulOperationTypeEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.GetTags;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.client.BaseClientInvocation;
import ca.uhn.fhir.rest.client.GetClientInvocation;
import ca.uhn.fhir.rest.method.SearchMethodBinding.RequestType;
import ca.uhn.fhir.rest.param.IParameter;
import ca.uhn.fhir.rest.param.ParameterUtil;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.rest.server.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

public class GetTagsMethodBinding extends BaseMethodBinding {

	private Class<? extends IResource> myType;
	private Integer myIdParamIndex;
	private Integer myVersionIdParamIndex;
	private String myResourceName;

	public GetTagsMethodBinding(Method theMethod, FhirContext theConetxt, Object theProvider, GetTags theAnnotation) {
		super(theMethod, theConetxt, theProvider);

		if (theProvider instanceof IResourceProvider) {
			myType = ((IResourceProvider) theProvider).getResourceType();
		} else {
			myType = theAnnotation.type();
		}
		
		if (!IResource.class.equals(myType)) {
			myResourceName = theConetxt.getResourceDefinition(myType).getName();
		}

		myIdParamIndex = ParameterUtil.findIdParameterIndex(theMethod);
		myVersionIdParamIndex = ParameterUtil.findVersionIdParameterIndex(theMethod);

		if (myIdParamIndex != null && myType.equals(IResource.class)) {
			throw new ConfigurationException("Method '" + theMethod.getName() + "' does not specify a resource type, but has an @" + IdParam.class.getSimpleName() + " parameter. Please specity a resource type in the @" + GetTags.class.getSimpleName() + " annotation");
		}
	}

	@Override
	public Object invokeClient(String theResponseMimeType, Reader theResponseReader, int theResponseStatusCode, Map<String, List<String>> theHeaders) throws IOException, BaseServerResponseException {
		if (theResponseStatusCode == Constants.STATUS_HTTP_200_OK) {
			IParser parser = createAppropriateParserForParsingResponse(theResponseMimeType, theResponseReader, theResponseStatusCode);
			TagList retVal = parser.parseTagList(theResponseReader);
			return retVal;
		} else {
			throw processNon2xxResponseAndReturnExceptionToThrow(theResponseStatusCode, theResponseMimeType, theResponseReader);
		}

	}

	@Override
	public String getResourceName() {
		return myResourceName;
	}

	@Override
	public RestfulOperationTypeEnum getResourceOperationType() {
		return null;
	}

	@Override
	public RestfulOperationSystemEnum getSystemOperationType() {
		return null;
	}

	@Override
	public BaseClientInvocation invokeClient(Object[] theArgs) throws InternalErrorException {
		GetClientInvocation retVal;

		IdDt id = null;
		IdDt versionId = null;
		if (myIdParamIndex != null) {
			id = (IdDt) theArgs[myIdParamIndex];
			if (myVersionIdParamIndex != null) {
				versionId = (IdDt) theArgs[myVersionIdParamIndex];
			}
		}

		if (myType != IResource.class) {
			if (id != null) {
				if (versionId != null) {
					retVal = new GetClientInvocation(getResourceName(), id.getValue(), Constants.PARAM_HISTORY, versionId.getValue(), Constants.PARAM_TAGS);
				} else {
					retVal = new GetClientInvocation(getResourceName(), id.getValue(), Constants.PARAM_TAGS);
				}
			} else {
				retVal = new GetClientInvocation(getResourceName(), Constants.PARAM_TAGS);
			}
		} else {
			retVal = new GetClientInvocation(Constants.PARAM_TAGS);
		}

		if (theArgs != null) {
			for (int idx = 0; idx < theArgs.length; idx++) {
				IParameter nextParam = getParameters().get(idx);
				nextParam.translateClientArgumentIntoQueryArgument(theArgs[idx], null, retVal);
			}
		}

		return retVal;
	}

	@Override
	public void invokeServer(RestfulServer theServer, Request theRequest, HttpServletResponse theResponse) throws BaseServerResponseException, IOException {
		Object[] params = createParametersForServerRequest(theRequest, null);

		if (myIdParamIndex != null) {
			params[myIdParamIndex] = theRequest.getId();
		}
		if (myVersionIdParamIndex != null) {
			params[myVersionIdParamIndex] = theRequest.getVersionId();
		}

		TagList resp = (TagList) invokeServerMethod(params);

		EncodingEnum responseEncoding = determineResponseEncoding(theRequest);

		theResponse.setContentType(responseEncoding.getResourceContentType());
		theResponse.setStatus(Constants.STATUS_HTTP_200_OK);
		theResponse.setCharacterEncoding(Constants.CHARSET_UTF_8);

		theServer.addHeadersToResponse(theResponse);

		IParser parser = responseEncoding.newParser(getContext());
		parser.setPrettyPrint(prettyPrintResponse(theRequest));
		PrintWriter writer = theResponse.getWriter();
		try {
			parser.encodeTagListToWriter(resp, writer);
		} finally {
			writer.close();
		}
	}

	@Override
	public boolean incomingServerRequestMatchesMethod(Request theRequest) {
		if (theRequest.getRequestType()!=RequestType.GET) {
			return false;
		}
		if (!Constants.PARAM_TAGS.equals(theRequest.getOperation())) {
			return false;
		}
		if (myResourceName == null) {
			if (getResourceName() != null) {
				return false;
			}
		} else if (!myResourceName.equals(theRequest.getResourceName())) {
			return false;

		}
		if ((myIdParamIndex != null) != (theRequest.getId() != null)) {
			return false;
		}
		if ((myVersionIdParamIndex != null) != (theRequest.getVersionId() != null)) {
			return false;
		}
		return true;
	}

}
