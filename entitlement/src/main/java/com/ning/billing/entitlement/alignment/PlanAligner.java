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

package com.ning.billing.entitlement.alignment;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.*;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * PlanAligner offers specific APIs to return the correct {@code TimedPhase} when creating, changing Plan or to compute next Phase on current Plan.
 * <p>
 *
 */
public class PlanAligner  {

    private final CatalogService catalogService;

    @Inject
    public PlanAligner(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    private enum WhichPhase {
        CURRENT,
        NEXT
    }

    /**
     * Returns the current and next phase for the subscription in creation
     * <p>
     * @param subscription the subscription in creation
     * @param plan the current Plan
     * @param initialPhase the initialPhase on which we should create that subscription. can be null
     * @param priceList the priceList
     * @param effectiveDate the effective creation date
     * @return
     * @throws CatalogApiException
     * @throws EntitlementUserApiException
     */
    public TimedPhase [] getCurrentAndNextTimedPhaseOnCreate(SubscriptionData subscription,
            Plan plan, PhaseType initialPhase, String priceList, DateTime effectiveDate)
        throws CatalogApiException, EntitlementUserApiException {
        List<TimedPhase> timedPhases = getTimedPhaseOnCreate(subscription, plan, initialPhase, priceList, effectiveDate);
        TimedPhase [] result = new TimedPhase[2];
        result[0] = getTimedPhase(timedPhases, effectiveDate, WhichPhase.CURRENT);
        result[1] = getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
        return result;
    }

    /**
     *
     * Returns current Phase for that Plan change
     * <p>
     * @param subscription the subscription in creation
     * @param plan the current Plan
     * @param priceList the priceList on which we should change that subscription.
     * @param effectiveDate the effective change date
     * @return
     * @throws CatalogApiException
     * @throws EntitlementUserApiException
     */
    public TimedPhase getCurrentTimedPhaseOnChange(SubscriptionData subscription,
            Plan plan, String priceList, DateTime effectiveDate)
        throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription, plan, priceList, effectiveDate, WhichPhase.CURRENT);
    }

    /**
     * Returns next Phase for that Plan change
     * <p>
     * @param subscription the subscription in creation
     * @param plan the current Plan
     * @param priceList the priceList on which we should change that subscription.
     * @param effectiveDate the effective change date
     * @return
     * @throws CatalogApiException
     * @throws EntitlementUserApiException
     */
    public TimedPhase getNextTimedPhaseOnChange(SubscriptionData subscription,
            Plan plan, String priceList, DateTime effectiveDate)
        throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription, plan, priceList, effectiveDate, WhichPhase.NEXT);
    }


    /**
     * Returns next Phase for that Subscription at a point in time
     * <p>
     * @param subscription the subscription for which we need to compute the next Phase event
     * @param effectiveDate the date at which we look to compute that event. effective needs to be after last Plan change or initial Plan
     * @return The PhaseEvent at the correct point in time
     */
    public TimedPhase getNextTimedPhase(SubscriptionData subscription, DateTime effectiveDate) {
        try {

            SubscriptionTransition lastPlanTransition = subscription.getInitialTransitionForCurrentPlan();
            if (effectiveDate.isBefore(lastPlanTransition.getEffectiveTransitionTime())) {
                throw new EntitlementError(String.format("Cannot specify an effectiveDate prior to last Plan Change, subscription = %s, effectiveDate = %s",
                        subscription.getId(), effectiveDate));
            }

            switch(lastPlanTransition.getTransitionType()) {
            // If we never had any Plan change, this a simple Phase alignement based on initial Phase and startPhaseDate
            case CREATE:
                DateTime initialStartPhase = subscription.getStartDate();
                PhaseType initialPhase = lastPlanTransition.getNextPhase().getPhaseType();
                List<TimedPhase> timedPhases = getPhaseAlignments(subscription.getCurrentPlan(), initialPhase, initialStartPhase);
                return getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
            // If we went through Plan changes, look at previous Plan Transition and borrow changePlan logics to recompute everything
            case CHANGE:
                return getTimedPhaseOnChange(subscription.getStartDate(),
                        subscription.getBundleStartDate(),
                        lastPlanTransition.getPreviousPhase(),
                        lastPlanTransition.getPreviousPlan(),
                        lastPlanTransition.getPreviousPriceList(),
                        lastPlanTransition.getNextPlan(),
                        lastPlanTransition.getNextPriceList(),
                        effectiveDate, WhichPhase.NEXT);
            default:
                throw new EntitlementError(String.format("Unexpectd initial transition %s for current plan %s on subscription %s",
                        lastPlanTransition.getTransitionType(), subscription.getCurrentPlan(), subscription.getId()));
            }
        } catch (Exception /*EntitlementUserApiException, CatalogApiException */ e) {
            throw new EntitlementError(String.format("Could not compute next phase change for subscription %s", subscription.getId()), e);
        }
    }

    private List<TimedPhase> getTimedPhaseOnCreate(SubscriptionData subscription,
            Plan plan, PhaseType initialPhase, String priceList, DateTime effectiveDate)
        throws CatalogApiException, EntitlementUserApiException  {

        Catalog catalog = catalogService.getCatalog();

        PlanSpecifier planSpecifier = new PlanSpecifier(plan.getProduct().getName(),
                plan.getProduct().getCategory(),
                plan.getBillingPeriod(),
                priceList);

        DateTime planStartDate = null;
        PlanAlignmentCreate alignement = null;
        alignement = catalog.planCreateAlignment(planSpecifier);

        switch(alignement) {
        case START_OF_SUBSCRIPTION:
            planStartDate = subscription.getStartDate();
            break;
        case START_OF_BUNDLE:
            planStartDate = subscription.getBundleStartDate();
            break;
        default:
            throw new EntitlementError(String.format("Unknwon PlanAlignmentCreate %s", alignement));
        }
        return getPhaseAlignments(plan, initialPhase, planStartDate);
    }


    private TimedPhase getTimedPhaseOnChange(SubscriptionData subscription,
            Plan nextPlan, String nextPriceList, DateTime effectiveDate, WhichPhase which)
        throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription.getStartDate(),
                subscription.getBundleStartDate(),
                subscription.getCurrentPhase(),
                subscription.getCurrentPlan(),
                subscription.getCurrentPriceList(),
                nextPlan,
                nextPriceList,
                effectiveDate,
                which);
    }

    private TimedPhase getTimedPhaseOnChange(DateTime subscriptionStartDate,
            DateTime bundleStartDate,
            PlanPhase currentPhase,
            Plan currentPlan,
            String currentPriceList,
            Plan nextPlan, String priceList, DateTime effectiveDate, WhichPhase which)
        throws CatalogApiException, EntitlementUserApiException {

        Catalog catalog = catalogService.getCatalog();
        ProductCategory currentCategory = currentPlan.getProduct().getCategory();
        if (currentCategory != ProductCategory.BASE) {
            throw new EntitlementError(String.format("Only implemented changePlan for BasePlan"));
        }

        PlanPhaseSpecifier fromPlanPhaseSpecifier = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                currentCategory,
                currentPlan.getBillingPeriod(),
                currentPriceList,
                currentPhase.getPhaseType());

        PlanSpecifier toPlanSpecifier = new PlanSpecifier(nextPlan.getProduct().getName(),
                nextPlan.getProduct().getCategory(),
                nextPlan.getBillingPeriod(),
                priceList);

        DateTime planStartDate = null;

        PlanAlignmentChange alignment = null;
        alignment = catalog.planChangeAlignment(fromPlanPhaseSpecifier, toPlanSpecifier);
        switch(alignment) {
        case START_OF_SUBSCRIPTION:
            planStartDate = subscriptionStartDate;
            break;
        case START_OF_BUNDLE:
            planStartDate = bundleStartDate;
            break;
        case CHANGE_OF_PLAN:
            throw new EntitlementError(String.format("Not implemented yet %s", alignment));
        case CHANGE_OF_PRICELIST:
            throw new EntitlementError(String.format("Not implemented yet %s", alignment));
        default:
            throw new EntitlementError(String.format("Unknwon PlanAlignmentChange %s", alignment));
        }
        List<TimedPhase> timedPhases = getPhaseAlignments(nextPlan, null, planStartDate);
        return getTimedPhase(timedPhases, effectiveDate, which);
    }


    private List<TimedPhase> getPhaseAlignments(Plan plan, PhaseType initialPhase, DateTime initialPhaseStartDate)
        throws EntitlementUserApiException {
        if (plan == null) {
            return Collections.emptyList();
        }

        List<TimedPhase> result = new LinkedList<TimedPhase>();
        DateTime curPhaseStart = (initialPhase == null) ? initialPhaseStartDate : null;
        DateTime nextPhaseStart = null;
        for (PlanPhase cur : plan.getAllPhases()) {
            // For create we can specify the phase so skip any phase until we reach initialPhase
            if (curPhaseStart == null) {
                if (initialPhase != cur.getPhaseType()) {
                    continue;
                }
                curPhaseStart = initialPhaseStartDate;
            }

            result.add(new TimedPhase(cur, curPhaseStart));

            if (cur.getPhaseType() != PhaseType.EVERGREEN) {
                Duration curPhaseDuration = cur.getDuration();
                nextPhaseStart = DefaultClock.addDuration(curPhaseStart, curPhaseDuration);
                if (nextPhaseStart == null) {
                    throw new EntitlementError(String.format("Unexpected non ending UNLIMITED phase for plan %s",
                            plan.getName()));
                }
                curPhaseStart = nextPhaseStart;
            }
        }
        if (initialPhase != null && curPhaseStart == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_PHASE, initialPhase);
        }
        return result;
    }

    private TimedPhase getTimedPhase(List<TimedPhase> timedPhases, DateTime effectiveDate, WhichPhase which) {
        TimedPhase cur = null;
        TimedPhase next = null;
        for (TimedPhase phase : timedPhases) {
            if (phase.getStartPhase().isAfter(effectiveDate)) {
                next = phase;
                break;
            }
            cur = phase;
        }
        switch(which) {
        case CURRENT:
            return cur;
        case NEXT:
            return next;
        default:
            throw new EntitlementError(String.format("Unepected %s TimedPhase", which));
        }
    }
}
