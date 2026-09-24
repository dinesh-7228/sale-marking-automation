package com.countrydelight.service;

import org.springframework.stereotype.Service;

/**
 * Autopay Customer Removed.
 *
 * Re-points the CUSTOMER column to 12345 in the `autopay_customers` table
 * across the beejapuri QA / UAT databases.
 */
@Service
public class AutopayCustomerRemovalService extends BaseCustomerRemovalService {

    @Override
    protected String getTableName() {
        return "autopay_customers";
    }

    @Override
    protected String getPreviewColumns() {
        return "ID, CUSTOMER, AUTOPAY_CONFIG, START_DATE, END_DATE, WALLET_AMOUNT, RECHARGE_AMOUNT, "
            + "TOTAL_TRANSACTIONS, TOTAL_RECHARGE_AMOUNT, TOTAL_CASHBACK, ACTIVE, CREATED_DATE, UPDATED_DATE";
    }
}