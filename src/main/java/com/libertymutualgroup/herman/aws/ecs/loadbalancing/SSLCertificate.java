/*
 * Copyright 2018 the original author or authors.
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
package com.libertymutualgroup.herman.aws.ecs.loadbalancing;

public class SSLCertificate {

    private String urlSuffix;
    private String urlPrefix;
    private String pathSuffix;
    private boolean internetFacingUrl;

    public String getUrlSuffix() {
        return urlSuffix;
    }

    public void setUrlSuffix(String urlSuffix) {
        this.urlSuffix = urlSuffix;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public String getPathSuffix() {
        return pathSuffix;
    }

    public void setPathSuffix(String pathSuffix) {
        this.pathSuffix = pathSuffix;
    }

    public boolean isInternetFacingUrl() {
        return internetFacingUrl;
    }

    public void setInternetFacingUrl(boolean internetFacingUrl) {
        this.internetFacingUrl = internetFacingUrl;
    }

    public SSLCertificate withUrlSuffix(final String urlSuffix) {
        this.urlSuffix = urlSuffix;
        return this;
    }

    public SSLCertificate withUrlPrefix(final String urlPrefix) {
        this.urlPrefix = urlPrefix;
        return this;
    }

    public SSLCertificate withPathSuffix(final String pathSuffix) {
        this.pathSuffix = pathSuffix;
        return this;
    }

    public SSLCertificate withInternetFacingUrl(final boolean internetFacingUrl) {
        this.internetFacingUrl = internetFacingUrl;
        return this;
    }

    @Override
    public String toString() {
        return "SSLCertificate{" +
            "urlSuffix='" + urlSuffix + '\'' +
            ", urlPrefix='" + urlPrefix + '\'' +
            ", pathSuffix='" + pathSuffix + '\'' +
            ", internetFacingUrl=" + internetFacingUrl +
            '}';
    }
}
