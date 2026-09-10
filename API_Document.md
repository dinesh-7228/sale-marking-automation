Step-1 Login to QA/UAT CRM
QA-url- https://qa-crm.countrydelight.in/login
UAT-url - https://uat-crm.countrydelight.in/login

step -2 Update the authorization value in all the API

Step -3 Add the search customer option with mobile number only
curl 'https://qa-cms.countrydelight.in/admin/v1/customers/searchCustomer/mobile_number' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'accept-language: en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7' \
  -H 'authorization: eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzg4OTMyOTU1LCJleHAiOjE3ODg5NjUzNTV9.0KlinYSgRgHEulXCIFhzryhVA7tB8Xi65Ky01XOJHjE' \
  -H 'content-type: application/json;charset=utf-8' \
  -H 'origin: https://qa-crm.countrydelight.in' \
  -H 'priority: u=1, i' \
  -H 'referer: https://qa-crm.countrydelight.in/' \
  -H 'sec-ch-ua: "Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "Linux"' \
  -H 'sec-fetch-dest: empty' \
  -H 'sec-fetch-mode: cors' \
  -H 'sec-fetch-site: same-site' \
  -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36'


Step-4 Ask the Product IDs Can be multiple with individual qty selection 
step - 4.1 One time order placement - in this case the start date and end date will be for the next date only 
curl 'https://uat-cms.countrydelight.in/admin/customers/v1/placeOrder' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'accept-language: en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7' \
  -H 'authorization: eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzg4OTM3OTUzLCJleHAiOjE3ODg5NzAzNTN9.QzhiVcjxUYY7NGoy4veDzJH2EFnN0oslmjAH3IVl65g' \
  -H 'content-type: application/json;charset=UTF-8' \
  -H 'origin: https://uat-crm.countrydelight.in' \
  -H 'priority: u=1, i' \
  -H 'referer: https://uat-crm.countrydelight.in/' \
  -H 'sec-ch-ua: "Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "Linux"' \
  -H 'sec-fetch-dest: empty' \
  -H 'sec-fetch-mode: cors' \
  -H 'sec-fetch-site: same-site' \
  -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36' \
  --data-raw '{"customer_id":"11145714","subscriptions":[{"id":0,"quantity":2,"order_start_date":"10-09-2026","order_end_date":"10-09-2026","source":"CMS","time_slot":13,"product":{"id":604},"frequency":{"id":1,"name":"One Time"}},{"id":0,"quantity":2,"order_start_date":"10-09-2026","order_end_date":"10-09-2026","source":"CMS","time_slot":13,"product":{"id":851},"frequency":{"id":1,"name":"One Time"}},{"id":0,"quantity":2,"order_start_date":"10-09-2026","order_end_date":"10-09-2026","source":"CMS","time_slot":13,"product":{"id":839},"frequency":{"id":1,"name":"One Time"}},{"id":0,"quantity":2,"order_start_date":"10-09-2026","order_end_date":"10-09-2026","source":"CMS","time_slot":13,"product":{"id":846},"frequency":{"id":1,"name":"One Time"}}],"order_amount":-1}'

  Step 4.2 - Subscription order place - in this case the end date will be null 

  curl 'https://uat-cms.countrydelight.in/admin/customers/v1/placeOrder' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'accept-language: en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7' \
  -H 'authorization: eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzg4OTM3OTUzLCJleHAiOjE3ODg5NzAzNTN9.QzhiVcjxUYY7NGoy4veDzJH2EFnN0oslmjAH3IVl65g' \
  -H 'content-type: application/json;charset=UTF-8' \
  -H 'origin: https://uat-crm.countrydelight.in' \
  -H 'priority: u=1, i' \
  -H 'referer: https://uat-crm.countrydelight.in/' \
  -H 'sec-ch-ua: "Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "Linux"' \
  -H 'sec-fetch-dest: empty' \
  -H 'sec-fetch-mode: cors' \
  -H 'sec-fetch-site: same-site' \
  -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36' \
  --data-raw '{"customer_id":"11145714","subscriptions":[{"id":0,"quantity":1,"order_start_date":"10-09-2026","order_end_date":null,"source":"CMS","time_slot":13,"product":{"id":795},"frequency":{"id":2,"name":"Daily"}},{"id":0,"quantity":1,"order_start_date":"10-09-2026","order_end_date":null,"source":"CMS","time_slot":13,"product":{"id":203},"frequency":{"id":2,"name":"Daily"}}],"order_amount":-1}'

Step- 5 Ask for order frequency 

Note - if the added product does not have that specific order_frequencies, like if the product do not have the daily then use the "id": 1,
                "name": "One Time" by default 
        "order_frequencies": [
            {
                "id": 10,
                "name": "Alternate"
            },
            {
                "id": 2,
                "name": "Daily"
            },
            {
                "id": 11,
                "name": "Custom"
            },
            {
                "id": 1,
                "name": "One Time"
            }
        ]


Step-6 If the user do not have enought wallet balance, the recharge the user wallet with this API 

IN this API customer_id is the id that is in the Step-2 during search, so use this ID
curl 'https://qa-cms.countrydelight.in/admin/v1/fundManagement/addFunds?additionDate=09-09-2026' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'accept-language: en-IN,en-GB;q=0.9,en-US;q=0.8,en;q=0.7' \
  -H 'authorization: eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIyNDA0IiwiaXNzIjoiQ291bnRyeURlbGlnaHQiLCJzdWIiOiJVc2VyIEF1dGhlbnRpY2F0aW9uIiwiaWF0IjoxNzg4OTMyOTU1LCJleHAiOjE3ODg5NjUzNTV9.0KlinYSgRgHEulXCIFhzryhVA7tB8Xi65Ky01XOJHjE' \
  -H 'content-type: application/json;charset=UTF-8' \
  -H 'origin: https://qa-crm.countrydelight.in' \
  -H 'priority: u=1, i' \
  -H 'referer: https://qa-crm.countrydelight.in/' \
  -H 'sec-ch-ua: "Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "Linux"' \
  -H 'sec-fetch-dest: empty' \
  -H 'sec-fetch-mode: cors' \
  -H 'sec-fetch-site: same-site' \
  -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36' \
  --data-raw '{"customers":[{"customer_id":9939343,"amount":1000,"remarks":"test BMS automation ","payment_type":7,"new_wallet_balance":5253.12}]}'

  Step-7 Once the order is placed successfully, then generate the route sheet, hit this API to generate the route sheet and show the route sheet generate successfully 
  note - in this API customerId is the same id from the step-2
  curl --location 'https://qa-cms.countrydelight.in/api/voice/generateRouteSheetByCustomerId?customerId=9951336'


Step-8 On placing the order ask the user for the sale_date on which date the user want to register the sale. By default current date 

Step -9 Update the route sheet date to the specific date selected by user in the Step-8
this is the query - select * from route_sheet_details rsd where rsd.CUSTOMER = 9951336 order by id desc;
 update as per the requrement and delivery_boy = 26747

 Once the route sheet is update properly 

 Step-10 Update the order_details date to 
 UPDATE order_detail
SET ORDER_START_DATE = ORDER_START_DATE - INTERVAL 1 DAY
WHERE CUSTOMER = 9951336
  AND STATUS = 'Y'
  AND ORDER_START_DATE >= CURRENT_DATE + INTERVAL 1 DAY
  AND ORDER_START_DATE < CURRENT_DATE + INTERVAL 2 DAY;


to the sale marking date (ORDER_START_DATE = sale_marking_date)

Step-11 Once dates are updated, this is the sale marking API 
curl --location 'https://qa-cms.countrydelight.in/api/delivery/sale_create' \
--header 'Content-Type: application/json' \
--data '{
    "action": "test",
    "data": {
        "delivered": true,
        "delivery": 645655970,
        "delivery_boy": 1,
        "delivery_id": 645655970,
        "delivery_time": "1788342166",
        "hold": false,
        "hold_end_date": "",
        "hold_start_date": "",
        "is_decrypt": true,
        "is_fnv": true,
        "is_geofenced_delivery": true,
        "location": {
            "accuracy": 6,
            "lat": 28.41873333333333,
            "lon": 77.03871166666666
        },
        "non_delivery_reason": "",
        "products": [
            
            
            
            
            
            
            
            
            
            {
                "change_type": "",
                "deliveryId": 645655970,
                "id": 300,
                "is_frozen": false,
                "is_packaging_required": true,
                "name": "Desi Danedar Ghee - 900 ML",
                "quantity": 2.00
            },
            {
                "change_type": "",
                "deliveryId": 645655970,
                "id": 1202,
                "is_frozen": false,
                "is_packaging_required": true,
                "name": "Desi Danedar Ghee - 900 ML",
                "quantity": 1.00
            },
            {
                "change_type": "",
                "deliveryId": 645655970,
                "id": 298,
                "is_frozen": false,
                "is_packaging_required": true,
                "name": "Desi Danedar Ghee - 900 ML",
                "quantity": 2.00
            }
        ],
        "quantity_changed": false,
        "remarks": "",
        "stop": false,
        "stop_start_date": "",
        "temperature": null,
        "time": null,
        "undo": false
    }
}'

in this API, the keys 
delivery and deliveryId  is the ID from the route_sheet_details table 

delivery time is the current delivery time with the sale_marking_date
delivery_boy - from the route_sheet_details

"change_type": "",
                "deliveryId": 645655970,
                "id": 298,
                "is_frozen": false,
                "is_packaging_required": true,
                "name": "Desi Danedar Ghee - 900 ML",
                "quantity": 2.00

                and in this JSON id and quantity is the product_id and quantity from the place order API 