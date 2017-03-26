package org.openhab.binding.isy.internal;

import org.openhab.binding.isy.internal.protocol.RestResponse;

public class RequestFailedException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final RestResponse response;

    public RequestFailedException(RestResponse response) {
        this.response = response;
    }

    public RestResponse getResponse() {
        return response;
    }

}
