/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.entitlement.engine.dao;

import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.tag.dao.TagDao;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.SubscriptionFactory;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.overdue.OverdueAccessApi;

public class MockEntitlementDaoSql extends EntitlementSqlDao implements MockEntitlementDao {

    private final ResetSqlDao resetDao;

    @Inject
    public MockEntitlementDaoSql(IDBI dbi, Clock clock, AddonUtils addonUtils, NotificationQueueService notificationQueueService,
                                 CustomFieldDao customFieldDao, final OverdueAccessApi overdueApi,
                                 final CatalogService catalogService) {
        super(dbi, clock, addonUtils, notificationQueueService, customFieldDao, overdueApi, catalogService);
        this.resetDao = dbi.onDemand(ResetSqlDao.class);
    }


    @Override
    public void reset() {
        resetDao.inTransaction(new Transaction<Void, ResetSqlDao>() {

            @Override
            public Void inTransaction(ResetSqlDao dao, TransactionStatus status)
                    throws Exception {
                resetDao.resetEvents();
                resetDao.resetSubscriptions();
                resetDao.resetBundles();
                resetDao.resetClaimedNotifications();
                resetDao.resetNotifications();
                return null;
            }
        });
    }

    public static interface ResetSqlDao extends Transactional<ResetSqlDao>, CloseMe {

        @SqlUpdate("truncate table entitlement_events")
        public void resetEvents();

        @SqlUpdate("truncate table subscriptions")
        public void resetSubscriptions();

        @SqlUpdate("truncate table bundles")
        public void resetBundles();

        @SqlUpdate("truncate table notifications")
        public void resetNotifications();

        @SqlUpdate("truncate table claimed_notifications")
        public void resetClaimedNotifications();

    }
}
