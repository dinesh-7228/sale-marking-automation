API-1
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" https://qa-cms.countrydelight.in/api/v2/customer/wallet/wallet_screen?action=

API-2
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" https://qa-cms.countrydelight.in/api/v2/customer/juspay/offers?amount=16000

API=3
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" --data $'{"mandate_enabled":false}' https://qa-cms.countrydelight.in/api/v2/customer/paymentMethods2/5?last_update=

API-4
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" --data $'{"payment_source":"2","mandate_enabled":false}' https://qa-cms.countrydelight.in/api/v2/customer/getSavedCardAndRecentPaymentsForCustomer
API-5 

API-6
curl -X POST -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" --data $'{"amount":"16000.0","autopay_new_flow":false,"customer_offer_id":"12777513","is_fomo":false,"is_updated":"true","mandate_enabled":false,"offer_id":"12777513","payment_method":"NB-DUMMY BANK","payment_source":"2"}' https://qa-cms.countrydelight.in/api/v2/customer/generateTransaction/2

API-7
curl -X GET -H "x-source: Android" -H "x-language: en" -H "x-os: 13" -H "x-app-version-name: 99.99.99" -H "x-app-version-code: 9999" -H "x-version-code: 9999" -H "x-chatbot-version: 80" -H "x-release-version: 33" -H "x-payment-version: 6" -H "x-rapid-version: 12" -H "Authorization: 6a2fbd79-6fae-41ed-86ff-64ef29e953ec6o-cd" https://qa-cms.countrydelight.in/api/v1/retail/transactions/JP98107882051790157554198/status

API-8
curl -X POST -H "accept: */*" -H "accept-encoding: gzip, deflate, br" -H "accept-language: en-US,en;q=0.9" -H "authorization: 0061274f-b80c-4786-8a3a-0ad33cafafb3Xv-cd" -H "content-length: 52" -H "content-type: application/json" -H "priority: u=3" -H "user-agent: CountryDelight/1 CFNetwork/3860.300.31 Darwin/25.2.0" -H "x-app-version-code: 234" -H "x-app-version-name: 9.9.99" -H "x-chatbot-version: 76" -H "x-payment-version: 5" -H "x-rapid-version: 11" -H "x-release-version: 76" -H "x-source: iOS" -d '{
"autopay_id" : 337,
"recharge_amount" : 2000
}' 'https://qa-cms.countrydelight.in/api/v2/customer/wallet/autopay_offers' --compressed

