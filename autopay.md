API-1
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" https://qa-cms.countrydelight.in/api/v2/customer/wallet/wallet_screen?action=

API-2
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" https://qa-cms.countrydelight.in/api/v2/customer/juspay/offers?amount=

API-3
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" https://qa-cms.countrydelight.in/api/offers/recharge/fetch

API-4
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" --data $'{"recharge_amount":"500.0","coupon_code":"EXPERIMENTALPLP200","is_fomo":"false","offer_id":"439"}' https://qa-cms.countrydelight.in/api/offers/recharge/apply

API-5
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" https://qa-cms.countrydelight.in/api/v2/customer/juspay/offers?amount=500

API-6 

curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" --data $'{"payment_source":"2","mandate_enabled":true}' https://qa-cms.countrydelight.in/api/v2/customer/getSavedCardAndRecentPaymentsForCustomer


API-7
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" --data $'{"mandate_enabled":true}' https://qa-cms.countrydelight.in/api/v2/customer/paymentMethods2/5?last_update=

API-8
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" --data $'{"amount":"500.0","autopay_id":"497","autopay_new_flow":false,"autopay_setup_source":"menu_screen","is_fomo":false,"is_updated":"true","mandate_amount":"0.0","mandate_duration":0,"mandate_enabled":true,"mandate_wallet_amount":"150.00","payment_source":"2"}' https://qa-cms.countrydelight.in/api/v2/customer/getAmountsAndCashBack

API-9
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" --data $'{"amount":"500.0","autopay_id":"497","autopay_new_flow":false,"autopay_setup_source":"menu_screen","is_fomo":false,"is_updated":"true","mandate_amount":"0.0","mandate_duration":0,"mandate_enabled":true,"mandate_wallet_amount":"150.00","payment_method":"CARD","payment_source":"2"}' https://qa-cms.countrydelight.in/api/v2/customer/generateTransaction/2

API-10
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 961a4c9e-8a1c-4706-af47-380f3b2c9b3c4O-cd" https://qa-cms.countrydelight.in/api/v1/retail/transactions/JP36936900001790664449766/status
























