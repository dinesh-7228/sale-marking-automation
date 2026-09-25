# -*- coding: utf-8 -*-
import io
"""Fix the two remaining corrupt tokens in PaymentService.java setupAutopay series."""
import io

p = "/home/dinesh/Documents/sale-marking-automation/src/main/java/com/countrydelight/service/PaymentService.java"
s = io.open(p, encoding="utf-8").read()

fix1_needle = r'matches("\d{10}")'
fix1_repl = r'matches("\\d{10}")'
fix2_needle = 'walletCredit Edgar);'
fix2_repl = 'walletCredit);'

c1 = s.count(fix1_needle)
c2 = s.count(fix2_needle)
print("before c1=%d c2=%d" % (c1, c2))

s2 = s.replace(fix1_needle, fix1_repl)
s3 = s2.replace(fix2_needle, fix2_repl)

print("after c1=%d c2=%d" % (s3.count(fix1_needle), s3.count(fix2_needle)))
io.open(p, "w", encoding="utf-8", newline="").write(s3)
print("FIXED_ATOMS")
