package com.countrydelight.service;

import org.springframework.stereotype.Service;

/**
 * Membership Customer Removed.
 *
 * Re-points the CUSTOMER column to 12345 in the `customer_membership_group`
 * table across the beejapuri QA / UAT databases.
 */
@Service
public class MembershipCustomerRemovalService extends BaseCustomerRemovalService {

    @Override
    protected String getTableName() {
        return "customer_membership_group";
    }

    @Override
    protected String getPreviewColumns() {
        return "ID, CUSTOMER, STATUS, START_DATE, END_DATE, EXPIRY_DATE, DISCOUNT, MAX_BENEFIT_AMOUNT, "
            + "REEDEMED_BENEFIT_AMOUNT, MEMBERSHIP_PLAN_DETAILS, IS_AUTO_RENEWAL, CREATED_DATE, UPDATED_DATE";
    }
}