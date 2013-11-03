/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.currency.api.CurrencyService;

public class DefaultCurrencyService implements CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCurrencyService.class);

    public static final String SERVICE_NAME = "currency-service";

    @Override
    public String getName() {
        return SERVICE_NAME;
    }
}