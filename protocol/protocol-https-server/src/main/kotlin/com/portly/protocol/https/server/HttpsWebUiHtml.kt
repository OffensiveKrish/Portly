package com.portly.protocol.https.server

import java.util.Base64

internal object HttpsWebUiHtml {

    const val LOGO_PNG_BASE64: String =
        "iVBORw0KGgoAAAANSUhEUgAAAJAAAACQCAYAAADnRuK4AAAk3UlEQVR4nO19CZgU1bX/r24tXV3T07MvMMDAsIhsGgRBwQ1c4kI0LjHqMxqjMYlm9WXR9/d7mmdilpfERBO3GKPGqIgYFRUlPjSCCi4QWWRxWGcYZpi1p5fqWv/fvdXV1IwD08N09yz07/uKobuqq27d+7vnnnvOuedytm0jhxyOFOSIf5lDDjkC5dBf5CRQDv1CjkA59As5AuXQL+QIlEO/kCNQDv2CgOEEatPivJ8BcN4vckg3hi6BmAHUawTlPksWrodrk9fkiJUOcEPHEm0nJMzhRl0bsMzE/zmA8Ie5NEGqw94vh6FPINv6bCNrEYLOpkKED1Qj1joZ0fYpMGOjYegVsPUAABE8p4IX2yEpdRD9u5BX+jH8JbXIK9sHuTgGInR9Rk8SLIchTCAmbTwNGmrIQ9Ons9C848uIHFgEYlXBJzjXUAKYVkKqsB8DhAN4evCAQACeONfFNMBXsAoFVc+gYNyrKKjeBTFgdlOcsv66QxWDi0BuWVzixCMEDZ9MR/2G76Oj4Rrk+RyiaDqVSmFwnAHOFgBbBoHASEKJQw92PwuwqATjNBCiOrywZIi8BNlH3x6Ic3tRMu7XKJn2NILjDhxW8uUwiAnkbbBYJ4/dHy7EnvW/A29MgWkCccMAx0VBKAFEiUkfShTdAHQzCsFXD9G3D4IYBjgDsH2AXgg9Vg0YI6D4AIF3dKS4DthcGAQWODvIiEmJ5it/FGUzf4riybuYFOouBXMYpARyG8rQgR3vz0XtmscgmJMQi9OzYdgQ4BNkRhr6nVK0HEUj/4Hi0auhFO2Dv7ATomyAl2xGQjasUaXbBIw4gRaRoLYXIdJ0DCL7FiDceBl4fTJE3hnSbIQAW4IiyoxkYtnDqJh7G4LjmruUL4dBRiBvwzTuLMKmFQ9B67jUIQ4XZkOTIglMOgRKn0XlsX9Aec1HCFZE+zW86DEO4foKtGy5EG07b4HfnsikkmZGQUCgSDIrW9GU61E5/1GIeVZuSBtsBHKHLPr8jSvPxvZ3XmMKr25E2QkqDQwDqJh0O8bOfBDlNQe6SIGkbSfxHTtHpY7nGexU4nv3N+w6z330CEHLlhloWv8zkPB5UHXAsMPgIKPQL8AQ12DUmReiYHzjwZvnpNHAEshtyFiY4L1/3I1Iw48QVQ3YnAqRC7ChqmjM3Zg075coHdNx8HdMIU6Phbm7wm4ZQMvGqWhY8wj4+ByEVQMcUeEjATabK5lzGkac/K8kSXND2gARyCVPW6OMd597EVroLKh6FDYIApIM3vchpi28DGOm70z8INHYGbTTeBV4PUrQ8M7FaP73s0zQsGGNk5g0Emq+ibHnPACOz+lFA0IgqssQAjTX5+Gtpz6ApU2GZlC9Q0KeLKBk7K343Od/DSVoZt9S3M3S3bGzBLteXwZBnYuI5pgACv0ySMVdGPfF25kh0u5NufYMnd0xTFwq2SOQ28tbG/x448kNMNXxMKwoOFuBJABTTp2Pqaes7kK0gYJbVi1MsPPVn8Pa/2OE4yps20KRXwFXeTdqLrqNSaIkEVz960hIb/esnw0BZIdAbuV0tol47a9roEU+B4NKHqJAFMOYff501MzY9Rm9ZCDhkoiSec+KryC27bEkiYoVBVzVjzD+ol8719EfdCONpXHQQz7onfkwY0HYmgLLFMERE7wUBZE7IeV3gA/EwStWt4cn/g6Cehg0BDJ1Dsv+vBixtksRZ8OWDEkMY/6lEzFqUhMz8B3O+TkQ8A5Ru9/4IiKblrLhzCWRdOxFGLPgBXbe0oFoQzGie2Yh2nA+1JbTYUZmwC+CSVjXOu4KGcO1qPt2wlfwL/hHvAx5zGr4qhpAZHuouFYySyBmzKN+KQL885nvoXnH7xCNR8ETAQJPcMolozFm8v5BSZ4uSDTiztevgLr17+hUVfAg8IkSyk4+EaYWROcnd8CKzIcsAoYJaAaVXho4XnNcLnQSYBFwxHJIZAvgIIDYMvsNnXmaNqCRbcgbew+UYxdDHtPymTIcdQSiFbd57RSse2MT4hrVeWjFy5h5zrGYPGvL4CdPt3fZtvQnMOruRmeMEkNAno8wZy2VJlSnc9woCmRegE90HLpU+rj17PrqqB+OQk+4VggJsTFTQAABH5gNjJT9GYGZd8I/sS5RiERhuKOAQG50YKhNxHP318HSi2FDg+JTMO64S3DSeUuHDHko3HqydA6bn3gBVscixC01ofposM0A8v0OmTpVA3LhSvhLV8JXtBFS/l4QKQzCG7AtHpaWByNcCaPjWBit86B1nIEAX8rqK0pdK1wIHCQoguw4iEf8LwJz74RUGU4UZtCQKHMEcmdSyx7/H7Ts+X9QtTBkMYBg2RO48OtfcUT64KiEPivWkUY/tj7ZCs6WYdlgztiopiJ/1B9QNOlvyB+zDVJRPOXZmNkpILavCtHaixDd/V0EuHHMIq5blDAC8iUZuqUicML5yJv9f06nGxwkygyBXHG/c0slVj7bAE1XwfO0d0Zx4ddHoLhCHbq+pUTDNW6YgKa3X4Flx1A85b9RNn055FL18GG33dDTtN0I84hunYnODb+EHD8DERUwEQZnKSgJEOglf0DwzFsgBI3BUIfpJ5A3qOvpPz6PcOtFMM0w/HIAU+YswOyFKwfczpOuDqJHqVJsQ0jMmpLGzz4aCd3h3usioWaAzo9PROdHT0K2xjMTAmVMgV+B4Xsf+ecsgDwiPNAkIhmr3O0bqxFpv8hxBfAKiG8VPnfqyuSsbCjDddyKiuWQx0Mc1ph9NAi6ksglDyUFkWwUzFqDissnA1W3Id8ns+iEjmgUdmQ2Qi9tg7q3yHleNzNSFpH+lqTkoCT54O27WPQgfTs6ZT/h9K9BEHs26w9JeAmSZgsyIwXlpQWIBQZKF90N8cTZECULAlEQ0VWY2gh0vroFan2B04z2MCAQC04HUL+rEG1N/8F0H8IFwPvfwuTjtuUCs/oCriuRgjM/QNG5o8BJjZCIDJWRqBwdr3wIvU3qErYydCVQohduXncxBBrwTlSIAjB9zg9Tcz7mcEgiURIpNY0o/sIkcDR8l6ckioI3xqP15cVMZ7KHMoFccsRVDp9u/iFUjRrWgtCsJkya8RG7JkeeIwcjkQn4q0IoOnc6CKG6pYRwPAp/9EK0vnFDUmINaR2oflc5LGMybDsMn0RQNf4eBBLhGTkC9Q8sDskC/GPaEDhjBmRRAEcI2mIq+N0PIvrpyGwPZemVQBQ7t5/G/D025yhEE6cuTvrEcug/3OEsOLUW9sQr2ezMsiyouoW2t5Y6QxmGIIHcqW3d7ougmwSmFUDcCGHU2N1D0uo8FEhUftpTiEorIIsKVCuKgDkHoQ9PT7bFkCNQuJNHS+NZ0AwLokBQVP4ygkVGbvjKAFinFIHiM650/g8BnXED4XWPwow67ZoFqU/SOn1vbc6HjVIQ0GAxoGLECud8bvhKPxJSRhnbDLPyLuQxf1kUAb4a4Q3zsqULpYlAib8tzSPZ9J2FNAAoq1yXlvvncHiUnPQrxHXaiyVENKBjw89h0ybwhJEMiVlYR9soRiaLIyzirqC4gX2f038yhARB5JGdQMX98Asy4mYUsjYfsb3liaFtCBEo1DGWhTdYpsTWrAeCnWm9fw49ICFhglN/77iRYIAQC5Hti5AFpIdALsvVWAkjkG0JMG0NPr+elvvncGi4nnilphZhsx6wA4hqBJG91zHDY4Z1oTRJoASDdKPAkUA01YrQDp4fODfxUQUbEBQLUsXfoEgEcUMDOudCb1Gc89xQcabajhHLibmyc7pPluAqysqYl5xoRSEKxUegNo52zltDhEAcYblSmBXaMH2wrJz1MBtwJym+8m1sNQho0i3ql2yenulHp4lAiR4g+doT4tJgjlRDH+KRY0MMYmEHIpoG05TZLDjePnVo2YFkfyNsFlxlgHAE0YjsnM8ZEjOLhATi/TqIbxdL90fXplnRCV3OZwDplRDBgl3sLwcDvECn9YVpvX8Oh4C75oxmaJOa2HSeSiAzXpEMtc1QJ04vgYqK65xpfMJ52nxgbFrvn0PPSK43pPXO0xyRSNjj8pIKNDeo7UCJ0pWWN0HTqQItsRdo2DfHOZ+Wp+SQCmzvYQ0xQ2JJaQw+3zaqDUHXgX31X4BpHExll0NmkOygLIlFMJHamE7nw0lDoz3YDYmUID6fjYqqF5lD1bQ0dIZORlNjwpiVQ8YZZKgEplHGVsPQr3i5IWmJzpA/Mv0RiTXjX3BS7ZIoRJFg+9bju5zPIQNI1K2pCrDVampEYQZF4ndyLtlDJiIRwISJ/2aZKkxLYVPJTRu+MSwWEw4FaO0FLOUMOJpCB5DLVx3MXmsNAQLRQo4aHUHliEfBCxJ0U0Pj/iuwry6PXUPH5hzSD1e6hxumOHuCgCCmWYjVL0J0b2FiRMjIKJAeArnEoIXcuaMQmlbOAr3BaeBFgo8+uMA5n5uOZRSxprmwORpCbLE0MVrjd1C/tA11z/8ckbrCLkunBw2B3EQJ4TDB4sXX4c8Pt6G55XzoNELOtphFurllBrs2pwdlBi4xguNfAKFJr6QgW04e06KI6Rq0ultRv6QN+1dcBUPlkkH5A56dwyVPbW0hlix5AeHOUxFXDQi8Rn3D8EmALG3BpVecjJrxbTldKAtpZzpqKxDa+iV01N4Chatme4EYND2MLaOIZt5XXkfF2ZdAGZmWzB5HTiCXPGvXTsBzSzcw249t0ULJ8MsCZPljnHzytzBz1rsIBq3cyoxswJN0Su/k0b55Llo/egCSOQ2hON0aS4MiKoDQirIzj0fBMXv7S6IjI5BLnrdXTceylz+GadDEkSo4TmHfz51zBRaeuRj5+Y6czK1KzR66p0rWwwQH3r0SnZufYPYhmsdR5Jzc3CULJ6CQLlA8chL1nUAuGda+X4Nnl9bCMjVYpgVJlOH3b8Gll5yBadP2s2vTmUgqE6tbk0Hnw1S5tz3ECG0fif3/XAVo41iaZZ5I8BEBpeeNQMHE/UeaMq9vBHLJs2t3APfe3wDToNsiGYw8pSWv49qvLEJFhZZ24mR69jbcJaSdIFKsSUbdiyuA6HzENBUikSD6mjHq8monPV/fSZQ6gdzr4hqHX/xmJdrbT4NtRcHzCkqK38BNN56DwkIzreTx3mt/vYL9+6oRCZXBtg+mdu2eTc5LBO6Q19jw+UMoKd+N8jEtEMXhTyTbdJIzqG0i9i5ZDSs6G3E9ioBPgV2wBOOuvCy5WV8fSJQ6gdzGfHrJ5Vj74dMw9Cg4jm6SshffvXly2iWPe69dOwqx6o27sHfnTWxWx3Ise5KCuevuad7lLp+913iGKy+ZaJphpfAtTJvzHUyd8/Gg2mohk5Io2ujH3md3wTCKYRkaivIUiNPORsUpK/raiVIjkNuYe+oU/OqejoQRga5/l3HNVVU4fsY+tq8p3TAuHXCf9+GaY/HaSxshEAJNM8Dz0UMShCViOgRh6NF9qwHHwi9AgAK/D6ic8B0suOTeZKLL4UoiJIaplo3j0b7yU0RpBl0Q8IKGcVeXQCqkJpiUpVBq4sKtzBdfoZnGaMC2CkGUMWP6Nxl5aIOnmzyfbi3Fa8s2M38aFbUcESCKQfAkCJ5LHOTgIXBBCMRzdDtPun2WBHqdAsNSEVajaNrxB6x+5UvD32fHOf2/ZFotuKpb2Zp6Ayp8fAD7V93Y1+XQvUsgt0H31vvxs9+EwHF0yi7A52vAHT+pRkEak0e5ZaFp/v/4238iHFoIw6Bp8mTI8hbMmvstVI7cCsKbn+kgh/zsXd7rCe+MhEqxdf330L7/eug6Xc1psLRx5301iIrRncNaH4Krz3aIqH18H0zDCT0WOA011xTBV+ysrklBCtFMCKnhX+9dAEESYBvt4IVCzJvzTUaedOd8ZimCt45EOLwQphUFEWQogTW45oZTUFyazpWujZg66wasWLwZddt/yzJb8LyETWuvQsXoBzCswTkk8hXqyD/229C3PYWw1g7FV4i2jWei8tRXUu1Ah295NwyDRheu//gWFqahmwGYVginzV+e9l7qSqAdn85jWc7oHhTUp3PG2Vcx8tDoRip+XZvQER804JzOSggw/4Lfw+L3wjIDiOsEDXu+xJTrDAaiDx7YQNnM59kWVvT9qQO2bct/Ou+fmve+dwJR7NhdiPbwHFgW1X0ETBh3P8pKHGmQCTEfCo2FaRGYtoK4pmF09V6HzHSHwMRUs18HcXQ2ek8lYKGw5DWIPHH0rWgN9LjzUsN2CKNIvJtcEodU+TD8kgDNVGFHzoDaGPBecjikNvZsqZ3OlunwvMruOmPqU6zyaeB8JmBaknN/S2A7/BCSuWXSjJhimK0kYc80BVhscdvwh51ov8KJj0OgkyCiwi8B4b3TEhekiUC79s5nuo5hKNA0YPy47axB3alxuuHuPZqNEcSJ2CNdVjMcNeCcP3lVWxCJ0w4rM39ZtHFel/NHTCBXOW46cCK7sQ0JfnkbyktjqT7giMEa8+A2FBkFW8uWpWcNJrhSXS6KwORr2V4cmgmoLXNTnYX1LoHUOIeO8CRH6eSBwoKP4Xf39MzUZnXeI1u7Snd75kAcAwXeZ0PK3+DsvGgCemwyLCdLYf+n8ZRAMbXUMRYKgJJXl5yhZFIvyfaQ4iUrlbwDoUDbnhfO1hZObjsSeQ+TJ1RFsbUymDoBEa3+E8ikOZ9BI/3pzQhEMTtp6zzbjmXlWe5BhzPT5JjE7QmZ4hXn3SrKLVQmH9jt9kQMgWbjsWCBs3yAmdKDeycQ9VxztgXLJmxbKjpLyTSyqf84SAS+EQ26VoFn/rQahNeTUsjrgGV+tR6+Y+C6XdPN/+aVLN5rHF+eCSVQi+LK11A1/VWUjEp01CxtbWmZcqKuCQvKT7HeeyeDT7LA853QjWLHBaBWsu8zHqOTwpaR6YJlirA5jaWloROFSHhO0uvfm5OWOwLH7qE+m+HToDZfh93rgTHHfQXTznoCvJhhEiVXtVY4q2vYkuhO8L0PXxS9D7Syz4YS2MUupWK9o/MYZpmmyJTiR4ueTR3IH9gJWZLA2Qp4gW5g0m0E6c3xRuFhRJJE3uu6MOyzn+nz6H4XkXgImmGgecvjeG/xL5hVOGMKduK+VGHWYxPZFuR0m3Je3sVSxfRbAtGCU4ttWdE6tHWcBlM30NJ+PFo7RFSk1S/VDVb2CETf8dTP/wH/t6wYQvtxsE2a3ZEc1A28w0w3CUN6GJrca7xxS8nfeYauLlKK6peWiEjHHMhiEKpmoCMaBhp/jE0r38b0M1/OyN6ormCLd4rQwsez6NI8SYAYXHdw6yiuHwSiCiVl5Oiqt7F9z/cg0HgcPojddRUoL6lLaxhHd2RDB3IlRGGJjouv+a+sGS97AiVIpF3E1neuRsOmR6DpEkJRA+G1f8f42SVQCjKw50jifSONI+AXZcT0EAt18Ze97Zzu/XmpUfqYce87xjb6gQPWb1p4cNaQySEsS4p00g7TXx8bd+QH9fPll+iYtegvKBn3E/gkCRaiTCLt25JIlpnuikg8u7X2bCcOiOpCNpA/em2qd+jFEp1g3+QJ9eDRCMMKMP3nw43/iVicc6ICMm1MzAKD3EYcKAOinTiovkMx6eT7mWOX9lDLttDWcGKioOl/dzPOobn2FkTjVAoGEDMbERy1L9XnHZ5AbqUWBS1MnnA/mxEQLoSYPg3vrx/PzmfKoToQvqmBkj6cJ0qAQs6Lse3Sqe/RtAk0tfRgpaQJTKfigKZPjoFsTYaJEAvtLai+D2Keo4T2Ox6IPShR6AUnP8xsQYYtsNnYiyt+6wSTZSBuJvt2oEEGzsm1nbH3dyMzTWDPu79nuaVNm0YhEJRPf6Qvd+qdQK4UOmHaPpQWLgdH6BaLUTS2L8KKVdMzIoW6+KRwdMLOYB24+l7dhzPBx89GzIjCx8uw8l5B0XhnhyU7nQSiBKHxIhefczPb5JUWwNANLH75JRxoEVhOmnTn/umPM3WgdRk7DU5Vr/RhW0ikyfnqmgMirSJ2r1qGCEttDwgCwZh5NzFl3h3eUkBq0yiWtIjaS2bXYsyIv4ATFFi2BlWvxn1/uydJnnT1lCMV3bQc7tRzKB4k4cQldJMajy2MI0Zypua93q2sVFO1uHVDlfVNLz4IYo+AZmjwSwq4oodRMWWXM7ylPrvum1+LvuDXL/82/us3dJ9OAaauonbPTXjwqY/wzav+kjSFp8jew6KvOhC93o1fisc5qLEUlyylcCLV1+H6eR2VNNT3GO3IY8ZMlnXVph7yQkQ7qGjwrKCkWxv4LIg+J1qzV5uNx7S+cdnNMNu+irCmQuJpKGsU0879bjLYvg9InUCMuRZQMzqKL19wCp5Z/j5MPQpDU7H6o0fgkyK47rJn2LX9XanhdWWkArfytm0rwXvvfgN1dZcgFqmGbTkWZdZjvdZgz2fXmuz97nD+rMSpQ96Xoje/V5dreigLm75bCvusxoHGnbdg2X03gHMt5FQawYLPvwclVc9j3Al/QsX45kOSKGnFtoENy65DuPZedMZVEM6C3yej8uQ5CJTHjsRQ2XcJRMlx4ZkfYM++G/HuhgdhalEYuoqVa55GKDwSN15xDws4oxGMpB/SKJWZmKsL0HK9/Mo5WL1qOXumwbwsdP3aoYnjNp4r/b2N63K/rwTg+kjGnsqSdKFwWvLdaXkMo/AzrhHbKkbLzuPRuvNOjJ75BUw/66WkbuRKJfZ/QhcLEGxadjsidXcgEldZhEXQryAw8VpUn7j+SF0lfQ/NcAv2raseQufDI7Dx0zuYJNLjKj7Y+FvU/e40fO2ya3HshPau+RP7SKZUlGiXPCvfnI333lsOw9DYPh00OxrddrynxueOoPF7IxSXgtRJfufR0dDdn+aVRpz0GcJ0vy/dVpSmseNsAU0bXsQWeR4mn/KOY5D02JUO1JbhkxXPwAqfgaimgoNDHt/oH2HKuY/1RWlOH4Fow/3w+jtxz19UrNv+CxhxDboexb7GC/E/97VgwUnX4IIFz6CyTO+q5HoqpDtcstF9xnozJLplaGsT8Opry2EZBiyLQJIUllavquoFcByNw+3aSC68na37eXf47d6Aro6QDPU4xL0ovL25CwE9//ee5zwX9ng/9wuX0DaPjsbzAX0aNN1AKKohvOo1jJlRxPxmFKEmGbXvfA2NW+5zCGdG2U4++YqMvJrvYMaie/vrjjqy4DBGIqrECcAPv/5L/HXJVix/53lYBt0jg6a5U/DW+09g1fu/wZzjb8f8Wc9h4rhWFhpyOLgNx/PxlIYvWo71Hx8HjhTDRhiipGD06D/hK1d/O5kdbThDjdyGtUt/hba6W2CYYch8ADs/Oh0lo/+NnR/eiJYdt8InKCw8hCMqRD4AkQBlx5+HY854NR3T5v4l2Uw6IAG8/UE1nvjHiwjHZsDQLIgkDNMKQvI5Fs/C/DWoGfM8xo56FyPKdyGgRCCKRpfeRWchdMfhJS/9EnvqvgFLp8FsUfz0tmKUluhdlDxXUX/i79/Axk1/hE6zTHAKvvXNIMaN7WTW8uGSKIHzRFYkbUP0/XmgpT4Pbz4aYj4zujyJQhYJRB7Mv0VICJwVRL4f4PxrcOzCi1E5eZ9Tl+7Njxz9C0/1NuYps3ZjyoSZWLL8Srz53kMAH4St0WClENuEt7VjDkKfzMHHWxM/TgRK9TS2O99b4DjHaHk4xNUCtorVtgXohoFgvjqggfGZwmeGvqTfLA6ToyQpZCSiaZU1IwrdpP8PQPEFoRkhVMy4HhPmPQdfXloTnqYnvtmdnZUUmrjxy0/g7PlLsPytK7D233dAN0ezd6Xr2qndCHRfVZ4GbguwvVvJeF7IsiQQj/iwUw39SJgajibY3S3XHI0iVZzOKGxB5dQ7MPZzLyBY7nSsNJKHIn0B8s6m945oHTcqxgyLXzr/MazbdAzWbbwYtXsvQ6hzBggvO9KBeGww3aSQMz13tpxJ2RbkGVKPJnCJOmKvzgGCVI+yCf+LygkvoWL8TiZxKLwW+jQivSssGAESbg96UIl05rzN7IjGfob9BxTsP1CK5rYRiISLoZt0T9WD4aO2TSAQHes2fg+t7ac68cApRDxmfQ1ZssXQxQI+UKB9jQ5fdKvv4tEP48SL7zl4zmMXygAys0THZbq7ZJhOexW/jZoxEXYAuw/7+5b28ejoPBWG6YzXvSEZLZklsMZwDYoZXmTZl87D1rUlCMXqPeE7yyAyu8aLRTQmXiDpRT7M9e7MytDllN0ZBz3W2Ql9pdDiHN549Sp0dkzHCXN/gwmTm9KeaOtIyua1PFODZRaQ+UWCLlIZf7nk7MlhQ6oE8pIok3CHq3+9cT62b34C8Tiwu/ZHmHfGKZi3YFXymmxLI9bZuumLWVIHB6ehxK0Md1bR6/XJtaXZQSQ8krkRTLOZ5Vdc86+38dIzN0GnaxMHYCbo7URZfvTgJFCXqXkvUsXrL8u01HYlyykL/go5byV4oRQ2DMTiKrZ/ch+efPhxtDaLSbNG1uAG5SPrGJwE6ktEYnclMhsEKinTcO2NZ2Jk9a8gy1Rfc/bmam68Gk8+tB47tpU6Zo0spW1hHW5gUsQMUgJ5FOKU9aAsBuHTZwXyLVx57Y8x9fjL2T6lFmSoRhSRyBQ893gDPnjn+KTel3HdjP6TtIUgmxicBHJ7lDuUpSyBslR53qn7uRctxoLzpkEkGvPFGXYUmknw9uvr8OrSq5N7uGeybNmUwkOCQHArIoWpObN5DEDlJf2AJjBr7iZcfu1I5OWth0Q3dLMtqHEV2zY+jmcf+ylLnM7KmoECenXFHIG6V0yKlT5AlQc30J1KmXET2vAfN56IEaPudb6zBcTUMPbvvR3bNo/J6NQ+20P4oJdA3sqwh8AaMkKNnwZQXKLjgkt+AMlXD5suf6InORUHGic4Zc1QAdn7D0xi9OwZEvsCUQwlVk9qsDgF0ZgAyzp0YvMu0irLlWgnhg+6B82e3flYtuQ5xNTRsE0LlkBgWjKqRm88ZNnTVg7XHnY0K9Huu48a8W9WNEogIhCsfPeCZHwPHS66HDgY/jpQIARY/2ENnnxkB9razoJp0UxndMdqBROmXofxxzRlzkI9cPrP4COQW78nznif7Ttv0rzFcQMrVj+NZW/MZsMEbSx60B5P//Kka/hrtirRu1hgxatn46Xna6HGS6EbUYCTwIsCZs8/FV+49NGMuzYGcAgfXEOYa3yrrorhxON+gI823wNTD8M0Anj65bX45+o3UFXxJghHpzU8CK9hx44rYRgWbEtgUYzZgOs4jcU4LH32x9j56d3Q6YoQjm6Kp0CS6rHokjmYMq0+K/6x7jOxo5ZADAll8PrL7sXtvzsfzR1nwbKjLMa5sXUhWjoWdl1LRXfwsS1wNB6YpSLOjkN1/34Zi596Cm2tF0GLO9F+sqyguOhlXHL5ZagcGUsSLSsSqA/Bd8N2CIMncLwg38LtN5+HidV/BC8pAFGYzYVufZk84nQpTyLiLkveeIq9e/Lw4ANb0HTgIsQ1OmQR5tKoqbkdX71xURfyZAMDMYQPWgJRuDmHyooN/PfNN+O6SyZjcs09CAa2gIAuUbGSRzZtIO5Q9NZbN8A0qgG7CRbL7Cph1tzzcdW1d0FR7KzHBuV0oF4WMC48aSsWnvR9hCM/QCQmdGnMvzxzJz6pvRW2pbG18NlA5cj3UbuDNlw5fEIrvnDhHJww69OkhMo6eRL/yelAhyCRS6RAno1AXtf0wrLcwiLwWPx0hhvL1WVOP301CHcy2tomYe7cxaiqcoasDASt91kHyrIdaBAq0d3gbRS3p3UJfzXFZEx0pnugWw6abGvBgncBvJuWbCTpQDadyUOKQF54V1IeTFhgD8iqDO9qBzLQ5HGHr+xLv6FFoEMh25bYgRqqUiFRLqS1H0qkW5FHE2x7QJ2pg3Ma3x8dwM3neDTATkwuTLpFk9uHsiuChi6BXI7I/haA7jHPHK8CGg4EsxJGOtCwE2aM9tYAeI6+M91Rm+ZMbM1mMYYugVxMGvsOSyzliHELjy19FLvr/YNOR0k36Ps1N8pY+cpDsFlKV2pYJagcc3B9WhbQv/xAg6EHhsIEN//0U8TUarZdkcBLgB1FVfkKEJKIIephB0HvX2+Sh+5ZwlyfW5fruUPfg6LHc1wKWdC6pb3zdoLuz7FsER3NC8HzAebIlQgBLx7AV78/Cnn5aU3hMjwJ5LW/vLm2Bg88XQtDoyGmToY0niRyJKaaw/BweRFT+A3Xw3Pc5Ft9vUdPJOrpNzR3AM9RX5yCPB/B/M9PxvTZW7NplxraBKJwK+uVt6bjby++CQvFLCNaIjvMIQnSF1JhgEjE9XINnTBIAtWbQ5h35umYNW9dtpdWD30CUbiVtqfBhxWrF2Hrji+io3O80xCeXNEuvI3QRdLQ0AvPfXvO4Zz4jtZbd+nQw/DW07DGjI89kOxwz+26Wa8z1coP7kDVmH9gxqwXUT5CHYh1+cODQBTdxbam97EyPYToKzhkH7TdRLohbwID5E4ZPgSiYJbYhC3oaIE1gE7cYUcgL4bre3kxCEwVw8MXNkgr92jAUSTrc8gEcgTKoV/IESiHfiFHoBz6hRyBcugXcgTKoV/IESiHfiFHoBzQH/x/ud1rL/6oqM4AAAAASUVORK5CYII="

    val LOGO_PNG_BYTES: ByteArray by lazy {
        Base64.getDecoder().decode(LOGO_PNG_BASE64)
    }

    const val FORMS_CSS: String = """/* Portly Web UI Stylesheet */"""

    fun loginPage(errorMessage: String? = null, lockedOutSeconds: Long = 0L): String = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Portly — Web Login</title>
    <link rel="icon" type="image/png" href="/assets/logo.png">
    <style>
        :root {
            --bg-base: #0B0F19;
            --bg-card: rgba(30, 41, 59, 0.7);
            --border-card: rgba(255, 255, 255, 0.08);
            --text-main: #F8FAFC;
            --text-sub: #94A3B8;
            --brand-primary: #FF5722;
            --brand-grad: linear-gradient(135deg, #FF6B4A 0%, #FF3D00 100%);
            --accent-teal: #00B4D8;
            --input-bg: rgba(15, 23, 42, 0.6);
            --input-border: #334155;
            --input-focus: #FF6B4A;
        }
        [data-theme="light"] {
            --bg-base: #F1F5F9;
            --bg-card: rgba(255, 255, 255, 0.85);
            --border-card: rgba(0, 0, 0, 0.08);
            --text-main: #0F172A;
            --text-sub: #64748B;
            --input-bg: #FFFFFF;
            --input-border: #CBD5E1;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background: var(--bg-base);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
            background-image: radial-gradient(circle at 50% 20%, rgba(255, 87, 34, 0.08) 0%, transparent 60%);
        }
        .login-card {
            width: 100%;
            max-width: 420px;
            background: var(--bg-card);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--border-card);
            border-radius: 20px;
            padding: 36px 32px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.25);
            text-align: center;
        }
        .logo-wrap {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 72px;
            height: 72px;
            border-radius: 18px;
            background: var(--brand-grad);
            box-shadow: 0 8px 24px rgba(255, 61, 0, 0.35);
            margin-bottom: 20px;
        }
        .brand-title {
            font-size: 24px;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 6px;
            background: var(--brand-grad);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .brand-subtitle {
            font-size: 13px;
            color: var(--text-sub);
            margin-bottom: 28px;
        }
        .alert-box {
            padding: 10px 14px;
            border-radius: 10px;
            font-size: 12px;
            margin-bottom: 20px;
            text-align: left;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .alert-error {
            background: rgba(239, 68, 68, 0.12);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #F87171;
        }
        .alert-warn {
            background: rgba(245, 158, 11, 0.12);
            border: 1px solid rgba(245, 158, 11, 0.3);
            color: #FBBF24;
        }
        .input-group {
            margin-bottom: 18px;
            text-align: left;
        }
        .input-label {
            display: block;
            font-size: 12px;
            font-weight: 600;
            margin-bottom: 6px;
            color: var(--text-sub);
        }
        .input-wrapper {
            position: relative;
            display: flex;
            align-items: center;
        }
        .input-field {
            width: 100%;
            padding: 12px 14px;
            background: var(--input-bg);
            border: 1px solid var(--input-border);
            border-radius: 10px;
            color: var(--text-main);
            font-size: 14px;
            outline: none;
            transition: all 0.2s;
        }
        .input-field:focus {
            border-color: var(--input-focus);
            box-shadow: 0 0 0 3px rgba(255, 107, 74, 0.2);
        }
        .pw-toggle {
            position: absolute;
            right: 12px;
            background: transparent;
            border: none;
            color: var(--text-sub);
            cursor: pointer;
            font-size: 14px;
            padding: 4px;
        }
        .remember-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 24px;
            font-size: 12px;
            color: var(--text-sub);
        }
        .remember-label {
            display: flex;
            align-items: center;
            gap: 6px;
            cursor: pointer;
        }
        .submit-btn {
            width: 100%;
            padding: 13px;
            border-radius: 12px;
            background: var(--brand-grad);
            border: none;
            color: #FFFFFF;
            font-size: 14px;
            font-weight: 700;
            cursor: pointer;
            box-shadow: 0 6px 18px rgba(255, 61, 0, 0.3);
            transition: all 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .submit-btn:hover {
            transform: translateY(-1px);
            box-shadow: 0 8px 24px rgba(255, 61, 0, 0.45);
        }
        .submit-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: none;
        }
        .footer-note {
            margin-top: 24px;
            font-size: 11px;
            color: var(--text-sub);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="logo-wrap">
            <svg width="40" height="40" viewBox="0 0 100 100" fill="none">
                <path d="M72 63 C78 63 83 58.5 83 52.5 C83 47 78.5 42.5 73 42 C71.5 32 63 25 52.5 25 C43 25 35 30.5 32.5 39 C31 38.5 29.5 38 28 38 C19.5 38 12.5 45 12.5 53.5 C12.5 62 19.5 69 28 69 L72 69" stroke="#ffffff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
                <path d="M30 46 L43 46 L49 52 L72 52 C74.2 52 76 53.8 76 56 L76 74 C76 76.2 74.2 78 72 78 L30 78 C27.8 78 26 76.2 26 74 L26 50 C26 47.8 27.8 46 30 46 Z" fill="#ffffff" stroke="#ffffff" stroke-width="2"/>
                <path d="M48 60 L56 66 L48 72" stroke="#FF451A" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
        </div>
        <div class="brand-title">Portly</div>
        <div class="brand-subtitle">Web File Transfer & Storage Console</div>

        ${if (!errorMessage.isNullOrBlank()) """
        <div class="alert-box alert-error">
            <span>⚠️</span>
            <span>$errorMessage</span>
        </div>
        """ else ""}

        ${if (lockedOutSeconds > 0) """
        <div class="alert-box alert-warn" id="lockoutBanner">
            <span>⏱️</span>
            <span>Too many failed attempts. Retry in <strong id="countdown">$lockedOutSeconds</strong>s.</span>
        </div>
        """ else ""}

        <form method="post" action="/login" id="loginForm">
            <div class="input-group">
                <label class="input-label" for="username">Username</label>
                <div class="input-wrapper">
                    <input type="text" id="username" name="username" class="input-field" placeholder="Enter username" required autofocus autocomplete="username"/>
                </div>
            </div>

            <div class="input-group">
                <label class="input-label" for="password">Password</label>
                <div class="input-wrapper">
                    <input type="password" id="password" name="password" class="input-field" placeholder="Enter password" required autocomplete="current-password"/>
                    <button type="button" class="pw-toggle" id="togglePw" title="Show / Hide Password">👁️</button>
                </div>
            </div>

            <div class="remember-row">
                <label class="remember-label">
                    <input type="checkbox" id="rememberMe"/>
                    <span>Remember username</span>
                </label>
                <span style="font-size: 11px; color: var(--text-sub);">Local device access</span>
            </div>

            <button type="submit" class="submit-btn" id="submitBtn">
                <span id="btnLabel">Sign In to Portly</span>
            </button>
        </form>

        <div class="footer-note">
            <span>🔒 Direct Local Network Storage Transfer</span>
        </div>
    </div>

    <script>
        const form = document.getElementById('loginForm');
        const submitBtn = document.getElementById('submitBtn');
        const btnLabel = document.getElementById('btnLabel');
        const pwInput = document.getElementById('password');
        const userInput = document.getElementById('username');
        const toggleBtn = document.getElementById('togglePw');
        const rememberCheckbox = document.getElementById('rememberMe');

        try {
            const saved = localStorage.getItem('portly_saved_user');
            if (saved) {
                userInput.value = saved;
                rememberCheckbox.checked = true;
                pwInput.focus();
            }
        } catch(e) {}

        if (toggleBtn) {
            toggleBtn.addEventListener('click', function() {
                const isPw = pwInput.type === 'password';
                pwInput.type = isPw ? 'text' : 'password';
                toggleBtn.textContent = isPw ? '🙈' : '👁️';
            });
        }

        let lockout = $lockedOutSeconds;
        if (lockout > 0) {
            submitBtn.disabled = true;
            const timerEl = document.getElementById('countdown');
            const iv = setInterval(function() {
                lockout--;
                if (timerEl) timerEl.textContent = lockout;
                if (lockout <= 0) {
                    clearInterval(iv);
                    submitBtn.disabled = false;
                    const b = document.getElementById('lockoutBanner');
                    if (b) b.style.display = 'none';
                }
            }, 1000);
        }

        form.addEventListener('submit', function() {
            if (rememberCheckbox.checked) {
                try { localStorage.setItem('portly_saved_user', userInput.value.trim()); } catch(e) {}
            } else {
                try { localStorage.removeItem('portly_saved_user'); } catch(e) {}
            }
            submitBtn.disabled = true;
            btnLabel.textContent = 'Connecting...';
        });
    </script>
</body>
</html>
""".trimIndent()

    fun fileBrowserPage(
        currentPath: String,
        readOnly: Boolean,
        requireAuth: Boolean,
        currentUser: String = "Authorized User",
    ): String = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Portly — Web File Manager</title>
    <link rel="icon" type="image/png" href="/assets/logo.png">
    <style>
        :root {
            --bg-base: #0B0F19;
            --bg-surface: #111827;
            --bg-card: #1E293B;
            --bg-card-hover: #273549;
            --border-subtle: #1E293B;
            --border-medium: #334155;
            --text-main: #F8FAFC;
            --text-secondary: #CBD5E1;
            --text-muted: #64748B;
            --brand-primary: #FF5722;
            --brand-grad: linear-gradient(135deg, #FF6B4A 0%, #FF3D00 100%);
            --accent-teal: #00B4D8;
            --accent-grad: linear-gradient(135deg, #00C9A7 0%, #00897B 100%);
            --danger: #EF4444;
            --danger-bg: rgba(239, 68, 68, 0.12);
            --success: #10B981;
            --success-bg: rgba(16, 185, 129, 0.12);
            --header-h: 68px;
            --radius-sm: 8px;
            --radius-md: 12px;
            --radius-lg: 16px;
        }
        [data-theme="light"] {
            --bg-base: #F8FAFC;
            --bg-surface: #FFFFFF;
            --bg-card: #FFFFFF;
            --bg-card-hover: #F1F5F9;
            --border-subtle: #F1F5F9;
            --border-medium: #E2E8F0;
            --text-main: #0F172A;
            --text-secondary: #475569;
            --text-muted: #94A3B8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background: var(--bg-base);
            color: var(--text-main);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
            font-size: 13px;
            line-height: 1.5;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }
        a { color: inherit; text-decoration: none; }

        /* Top Header */
        .portly-header {
            height: var(--header-h);
            background: var(--bg-surface);
            border-bottom: 1px solid var(--border-medium);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 24px;
            position: sticky;
            top: 0;
            z-index: 100;
            backdrop-filter: blur(12px);
        }
        .header-left {
            display: flex;
            align-items: center;
            gap: 16px;
        }
        .brand-link {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .brand-logo-wrap {
            width: 40px;
            height: 40px;
            border-radius: 10px;
            background: var(--brand-grad);
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 12px rgba(255, 61, 0, 0.3);
        }
        .brand-text {
            display: flex;
            flex-direction: column;
        }
        .brand-title {
            font-size: 18px;
            font-weight: 800;
            letter-spacing: -0.4px;
            background: var(--brand-grad);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            line-height: 1.2;
        }
        .brand-badge {
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.6px;
            color: var(--accent-teal);
        }

        .header-center {
            display: flex;
            align-items: center;
        }
        .device-info-pill {
            background: var(--bg-card);
            border: 1px solid var(--border-medium);
            border-radius: 30px;
            padding: 6px 16px;
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 12px;
        }
        .storage-track {
            width: 100px;
            height: 6px;
            background: var(--border-medium);
            border-radius: 4px;
            overflow: hidden;
        }
        .storage-fill {
            height: 100%;
            background: var(--brand-grad);
            width: 0%;
            border-radius: 4px;
            transition: width 0.3s;
        }

        .header-right {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .header-btn {
            background: var(--bg-card);
            border: 1px solid var(--border-medium);
            color: var(--text-main);
            padding: 7px 12px;
            border-radius: 8px;
            cursor: pointer;
            font-size: 12px;
            font-weight: 600;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.15s;
        }
        .header-btn:hover {
            background: var(--bg-card-hover);
        }}
        .user-chip {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 5px 12px;
            background: var(--bg-card);
            border: 1px solid var(--border-medium);
            border-radius: 30px;
            font-size: 12px;
            font-weight: 600;
        }

        /* Main Container */
        .app-body {
            flex: 1;
            max-width: 1400px;
            width: 100%;
            margin: 0 auto;
            padding: 20px 24px 60px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        /* Breadcrumbs Trail & Shortcuts */
        .nav-card {
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-md);
            padding: 12px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
        }
        .breadcrumbs-wrap {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            font-weight: 500;
            flex: 1;
            min-width: 200px;
        }
        .crumb-item {
            color: var(--accent-teal);
            cursor: pointer;
            padding: 2px 6px;
            border-radius: 4px;
            transition: background 0.15s;
        }
        .crumb-item:hover {
            background: var(--bg-card);
        }
        .crumb-sep {
            color: var(--text-muted);
        }
        .crumb-current {
            color: var(--text-main);
            font-weight: 700;
        }
        .shortcuts-bar {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-wrap: wrap;
        }
        .shortcut-chip {
            background: var(--bg-card);
            border: 1px solid var(--border-medium);
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 600;
            padding: 4px 10px;
            border-radius: 20px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .shortcut-chip:hover {
            background: var(--brand-primary);
            color: #FFFFFF;
            border-color: var(--brand-primary);
        }

        /* Action Toolbar */
        .action-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            flex-wrap: wrap;
        }
        .actions-left {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }
        .actions-right {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 14px;
            font-size: 12px;
            font-weight: 600;
            border-radius: var(--radius-sm);
            border: 1px solid var(--border-medium);
            background: var(--bg-card);
            color: var(--text-main);
            cursor: pointer;
            transition: all 0.15s;
        }
        .btn:hover {
            background: var(--bg-card-hover);
        }
        .btn-primary {
            background: var(--brand-grad);
            border-color: transparent;
            color: #FFFFFF;
            box-shadow: 0 4px 12px rgba(255, 61, 0, 0.25);
        }
        .btn-primary:hover {
            box-shadow: 0 6px 16px rgba(255, 61, 0, 0.4);
            transform: translateY(-1px);
        }
        .search-wrap {
            position: relative;
            width: 240px;
        }
        .search-box {
            width: 100%;
            padding: 8px 12px 8px 32px;
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-sm);
            color: var(--text-main);
            font-size: 12px;
            outline: none;
        }
        .search-box:focus {
            border-color: var(--brand-primary);
        }
        .search-icon-pos {
            position: absolute;
            left: 10px;
            top: 50%;
            transform: translateY(-50%);
            color: var(--text-muted);
            pointer-events: none;
        }
        .view-btn-group {
            display: flex;
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-sm);
            overflow: hidden;
        }
        .view-btn {
            background: var(--bg-surface);
            border: none;
            padding: 6px 10px;
            color: var(--text-muted);
            cursor: pointer;
        }
        .view-btn.active {
            background: var(--bg-card);
            color: var(--brand-primary);
        }

        /* Category Filter Pills */
        .filter-pills-row {
            display: flex;
            align-items: center;
            gap: 6px;
            overflow-x: auto;
            padding-bottom: 4px;
        }
        .filter-pill {
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            color: var(--text-secondary);
            font-size: 11px;
            font-weight: 600;
            padding: 5px 12px;
            border-radius: 20px;
            cursor: pointer;
            white-space: nowrap;
            transition: all 0.15s;
        }
        .filter-pill.active {
            background: var(--bg-card);
            border-color: var(--brand-primary);
            color: var(--brand-primary);
        }

        /* Drag & Drop Hero */
        .dropzone-hero {
            border: 2px dashed var(--border-medium);
            background: var(--bg-surface);
            border-radius: var(--radius-md);
            padding: 24px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
        }
        .dropzone-hero:hover, .dropzone-hero.dragover {
            border-color: var(--brand-primary);
            background: rgba(255, 87, 34, 0.04);
        }

        /* File Table (List View) */
        .table-wrap {
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-md);
            overflow: hidden;
        }
        .portly-table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 12px;
        }
        .portly-table th {
            background: var(--bg-card);
            color: var(--text-secondary);
            font-weight: 700;
            padding: 10px 14px;
            border-bottom: 1px solid var(--border-medium);
            cursor: pointer;
            user-select: none;
            white-space: nowrap;
        }
        .portly-table td {
            padding: 9px 14px;
            border-bottom: 1px solid var(--border-subtle);
            color: var(--text-main);
        }
        .portly-table tr:hover td {
            background: var(--bg-card-hover);
        }
        .file-name-cell {
            display: flex;
            align-items: center;
            gap: 10px;
            font-weight: 500;
        }
        .file-icon {
            font-size: 16px;
            flex-shrink: 0;
        }
        .file-link {
            color: var(--text-main);
            cursor: pointer;
        }
        .file-link:hover {
            color: var(--brand-primary);
            text-decoration: underline;
        }
        .file-badge {
            font-size: 10px;
            font-weight: 700;
            padding: 2px 6px;
            border-radius: 4px;
            text-transform: uppercase;
            background: var(--bg-card);
            color: var(--accent-teal);
            border: 1px solid var(--border-medium);
        }

        /* Grid View */
        .grid-wrap {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
            gap: 14px;
        }
        .grid-card {
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-md);
            padding: 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            position: relative;
            cursor: pointer;
            transition: all 0.2s;
        }
        .grid-card:hover {
            border-color: var(--brand-primary);
            transform: translateY(-2px);
            box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
        }
        .grid-icon {
            font-size: 38px;
            margin: 8px 0 12px;
        }
        .grid-name {
            font-size: 12px;
            font-weight: 600;
            width: 100%;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            margin-bottom: 4px;
        }
        .grid-meta {
            font-size: 11px;
            color: var(--text-muted);
        }
        .grid-check {
            position: absolute;
            top: 10px;
            left: 10px;
        }

        /* Floating Batch Action Bar */
        .batch-floating-bar {
            position: fixed;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%);
            background: var(--bg-card);
            border: 1px solid var(--brand-primary);
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.4);
            border-radius: 30px;
            padding: 8px 20px;
            display: flex;
            align-items: center;
            gap: 14px;
            z-index: 200;
            backdrop-filter: blur(12px);
            animation: slideUp 0.2s ease-out;
        }
        @keyframes slideUp {
            from { transform: translate(-50%, 40px); opacity: 0; }
            to { transform: translate(-50%, 0); opacity: 1; }
        }

        /* Upload Progress Drawer */
        .upload-drawer {
            position: fixed;
            bottom: 24px;
            right: 24px;
            width: 340px;
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-md);
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
            padding: 16px;
            z-index: 200;
            display: none;
        }
        .progress-bar-wrap {
            height: 8px;
            background: var(--border-medium);
            border-radius: 4px;
            overflow: hidden;
            margin: 8px 0;
        }
        .progress-bar-fill {
            height: 100%;
            background: var(--brand-grad);
            width: 0%;
            transition: width 0.15s;
        }

        /* Modal Overlays */
        .modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.65);
            backdrop-filter: blur(8px);
            z-index: 300;
            display: none;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .modal-box {
            background: var(--bg-surface);
            border: 1px solid var(--border-medium);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 520px;
            max-height: 90vh;
            overflow-y: auto;
            padding: 24px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
        }
        .modal-large {
            max-width: 900px;
            width: 95vw;
        }

        /* Toast */
        .toast-container {
            position: fixed;
            top: 20px;
            right: 20px;
            z-index: 500;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .toast-item {
            background: var(--bg-card);
            border: 1px solid var(--border-medium);
            padding: 10px 16px;
            border-radius: 8px;
            font-size: 12px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
            display: flex;
            align-items: center;
            gap: 8px;
            animation: fadeIn 0.2s;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(-10px); }
            to { opacity: 1; transform: translateY(0); }
        }
    </style>
</head>
<body>
    <!-- Top Header -->
    <header class="portly-header">
        <div class="header-left">
            <a href="/" class="brand-link">
                <div class="brand-logo-wrap">
                    <svg width="24" height="24" viewBox="0 0 100 100" fill="none">
                        <path d="M72 63 C78 63 83 58.5 83 52.5 C83 47 78.5 42.5 73 42 C71.5 32 63 25 52.5 25 C43 25 35 30.5 32.5 39 C31 38.5 29.5 38 28 38 C19.5 38 12.5 45 12.5 53.5 C12.5 62 19.5 69 28 69 L72 69" stroke="#ffffff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
                        <path d="M30 46 L43 46 L49 52 L72 52 C74.2 52 76 53.8 76 56 L76 74 C76 76.2 74.2 78 72 78 L30 78 C27.8 78 26 76.2 26 74 L26 50 C26 47.8 27.8 46 30 46 Z" fill="#ffffff" stroke="#ffffff" stroke-width="2"/>
                        <path d="M48 60 L56 66 L48 72" stroke="#FF451A" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                </div>
                <div class="brand-text">
                    <span class="brand-title">Portly</span>
                    <span class="brand-badge">Web Console</span>
                </div>
            </a>
        </div>

        <div class="header-center">
            <div class="device-info-pill">
                <span>📱</span>
                <span id="deviceModelText" style="font-weight: 600;">Connected Storage</span>
                <div class="storage-track">
                    <div class="storage-fill" id="storageBarFill"></div>
                </div>
                <span id="storageSummaryText" style="color: var(--text-muted);">--</span>
            </div>
        </div>

        <div class="header-right">
            <button type="button" class="header-btn" onclick="toggleTheme()" title="Switch Theme">
                <span id="themeIcon">☀️</span>
            </button>
            <div class="user-chip">
                <span>👤</span>
                <span>$currentUser</span>
            </div>
            ${if (requireAuth) """
            <form method="post" action="/logout" style="margin: 0;">
                <button type="submit" class="header-btn" title="End Session">Log Out</button>
            </form>
            """ else ""}
        </div>
    </header>

    <!-- Main Container -->
    <main class="app-body">
        <!-- Navigation Trail & Quick Jump -->
        <div class="nav-card">
            <div class="breadcrumbs-wrap" id="breadcrumbBar">
                <span class="crumb-item" onclick="navigateTo('/')">📁 Root</span>
            </div>
            <div class="shortcuts-bar">
                <button type="button" class="shortcut-chip" onclick="navigateTo('/')">🏠 Root</button>
                <button type="button" class="shortcut-chip" onclick="navigateTo('/Download')">📥 Downloads</button>
                <button type="button" class="shortcut-chip" onclick="navigateTo('/DCIM')">📷 DCIM</button>
                <button type="button" class="shortcut-chip" onclick="navigateTo('/Documents')">📄 Documents</button>
                <button type="button" class="shortcut-chip" onclick="navigateTo('/Music')">🎵 Music</button>
                <button type="button" class="shortcut-chip" onclick="navigateTo('/Movies')">🎬 Movies</button>
            </div>
        </div>

        <!-- Action Bar -->
        <div class="action-bar">
            <div class="actions-left">
                ${if (!readOnly) """
                <input type="file" id="filePicker" multiple style="display:none;" onchange="handleFileSelect(event)" />
                <input type="file" id="folderPicker" webkitdirectory style="display:none;" onchange="handleFileSelect(event)" />
                <button type="button" class="btn btn-primary" onclick="document.getElementById('filePicker').click()">
                    <span>📤 Upload Files</span>
                </button>
                <button type="button" class="btn" onclick="document.getElementById('folderPicker').click()">
                    <span>📁 Upload Folder</span>
                </button>
                <button type="button" class="btn" onclick="openNewFolderModal()">
                    <span>➕ New Folder</span>
                </button>
                """ else ""}
                <button type="button" class="btn" onclick="refreshList()">
                    <span>🔄 Refresh</span>
                </button>
            </div>

            <div class="actions-right">
                <div class="search-wrap">
                    <span class="search-icon-pos">🔍</span>
                    <input type="text" id="searchInput" class="search-box" placeholder="Filter in current folder..." oninput="handleSearch(this.value)" />
                </div>
                <div class="view-btn-group">
                    <button type="button" class="view-btn active" id="listViewBtn" onclick="setViewMode('list')" title="List View">☰</button>
                    <button type="button" class="view-btn" id="gridViewBtn" onclick="setViewMode('grid')" title="Grid View">⊞</button>
                </div>
            </div>
        </div>

        <!-- Category Filters -->
        <div class="filter-pills-row">
            <button type="button" class="filter-pill active" onclick="setCategoryFilter('ALL', this)">All Files (<span id="countAll">0</span>)</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('DIR', this)">📁 Folders</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('IMG', this)">🖼️ Images</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('VID', this)">🎬 Videos</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('AUD', this)">🎵 Audio</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('DOC', this)">📄 Documents</button>
            <button type="button" class="filter-pill" onclick="setCategoryFilter('ARCHIVE', this)">📦 Archives</button>
        </div>

        <!-- Drag & Drop Zone -->
        ${if (!readOnly) """
        <div class="dropzone-hero" id="dropzone" onclick="document.getElementById('filePicker').click()">
            <div style="font-size: 24px; margin-bottom: 4px;">☁️</div>
            <div style="font-weight: 700; margin-bottom: 2px;">Drag & Drop files or folders here to upload</div>
            <div style="color: var(--text-muted); font-size: 11px;">Supports batch transfers and high-speed multi-file streaming directly to connected Android storage</div>
        </div>
        """ else ""}

        <!-- Content Views -->
        <div id="listViewContainer" class="table-wrap">
            <table class="portly-table">
                <thead>
                    <tr>
                        <th style="width: 36px; text-align: center;"><input type="checkbox" id="selectAllCheckbox" onchange="toggleSelectAll(this.checked)" /></th>
                        <th onclick="sortTable('name')">Name ⬍</th>
                        <th onclick="sortTable('size')">Size ⬍</th>
                        <th onclick="sortTable('modified')">Modified ⬍</th>
                        <th>Type</th>
                        <th style="text-align: right;">Actions</th>
                    </tr>
                </thead>
                <tbody id="tableBody">
                    <tr><td colspan="6" style="text-align:center; padding: 32px; color: var(--text-muted);">Loading storage contents...</td></tr>
                </tbody>
            </table>
        </div>

        <div id="gridViewContainer" class="grid-wrap" style="display: none;"></div>
    </main>

    <!-- Floating Batch Selection Bar -->
    <div class="batch-floating-bar" id="batchBar" style="display: none;">
        <span style="font-weight: 700;" id="batchCountText">0 selected</span>
        <button type="button" class="btn btn-primary" onclick="downloadSelectedZip()">📦 Download as ZIP</button>
        ${if (!readOnly) """
        <button type="button" class="btn" style="color: var(--danger);" onclick="deleteSelectedBatch()">🗑️ Delete Selected</button>
        """ else ""}
        <button type="button" class="btn" onclick="clearSelection()">✕ Cancel</button>
    </div>

    <!-- Upload Progress Drawer -->
    <div class="upload-drawer" id="uploadDrawer">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <strong id="uploadDrawerTitle" style="font-size: 12px;">Uploading Files...</strong>
            <button type="button" style="background:none; border:none; color:var(--text-muted); cursor:pointer;" onclick="cancelUpload()">✕</button>
        </div>
        <div class="progress-bar-wrap">
            <div class="progress-bar-fill" id="uploadProgressFill"></div>
        </div>
        <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted);">
            <span id="uploadBytesText">0 / 0</span>
            <span id="uploadSpeedText">-- MB/s</span>
        </div>
    </div>

    <!-- Modals -->
    <!-- New Folder Modal -->
    <div class="modal-overlay" id="newFolderModal">
        <div class="modal-box">
            <h3 style="margin-bottom: 14px;">Create New Directory</h3>
            <input type="text" id="newFolderName" class="search-box" style="margin-bottom: 16px; width: 100%;" placeholder="e.g. My Documents" />
            <div style="display: flex; justify-content: flex-end; gap: 8px;">
                <button type="button" class="btn" onclick="closeModal('newFolderModal')">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="submitCreateFolder()">Create</button>
            </div>
        </div>
    </div>

    <!-- Rename Modal -->
    <div class="modal-overlay" id="renameModal">
        <div class="modal-box">
            <h3 style="margin-bottom: 14px;">Rename File</h3>
            <input type="text" id="renameNewName" class="search-box" style="margin-bottom: 16px; width: 100%;" />
            <div style="display: flex; justify-content: flex-end; gap: 8px;">
                <button type="button" class="btn" onclick="closeModal('renameModal')">Cancel</button>
                <button type="button" class="btn btn-primary" onclick="submitRename()">Rename</button>
            </div>
        </div>
    </div>

    <!-- Lightbox / Preview Modal -->
    <div class="modal-overlay" id="previewModal">
        <div class="modal-box modal-large" style="text-align: center;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <strong id="previewTitle" style="font-size: 14px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">Preview</strong>
                <div style="display: flex; gap: 6px;">
                    <a id="previewDownloadBtn" class="btn" download>Download</a>
                    <button type="button" class="btn" onclick="closeModal('previewModal')">✕</button>
                </div>
            </div>
            <div id="previewContent" style="min-height: 240px; display: flex; align-items: center; justify-content: center;"></div>
        </div>
    </div>

    <!-- Toast Container -->
    <div class="toast-container" id="toastContainer"></div>

    <script>
        let currentPath = '$currentPath' || '/';
        let rawEntries = [];
        let filteredEntries = [];
        let selectedPaths = new Set();
        let viewMode = localStorage.getItem('portly_view_mode') || 'list';
        let sortKey = 'name';
        let sortAsc = true;
        let searchQuery = '';
        let categoryFilter = 'ALL';
        let activeXhr = null;
        let renameTargetPath = null;

        const isReadOnly = ${readOnly};
        const currentUser = "$currentUser";

        // Theme management
        function applySavedTheme() {
            const saved = localStorage.getItem('portly_theme') || 'dark';
            document.documentElement.setAttribute('data-theme', saved);
            document.getElementById('themeIcon').textContent = saved === 'dark' ? '☀️' : '🌙';
        }
        function toggleTheme() {
            const cur = document.documentElement.getAttribute('data-theme') || 'dark';
            const next = cur === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('portly_theme', next);
            document.getElementById('themeIcon').textContent = next === 'dark' ? '☀️' : '🌙';
        }
        applySavedTheme();

        // Load Device Storage info
        async function loadDeviceInfo() {
            try {
                const res = await fetch('/api/device-info');
                if (res.ok) {
                    const info = await res.json();
                    document.getElementById('deviceModelText').textContent = info.deviceName || 'Android Storage';
                    document.getElementById('storageBarFill').style.width = (info.usedPercent || 0) + '%';
                    document.getElementById('storageSummaryText').textContent = info.freeFormatted + ' free of ' + info.totalFormatted;
                }
            } catch(e) {}
        }
        loadDeviceInfo();

        // Directory loading
        async function loadDirectory(path) {
            currentPath = path || '/';
            selectedPaths.clear();
            updateBatchBar();
            updateBreadcrumbs();
            const tb = document.getElementById('tableBody');
            tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 32px; color: var(--text-muted);">Loading storage contents...</td></tr>';
            try {
                const res = await fetch('/api/list?path=' + encodeURIComponent(currentPath));
                if (res.status === 401) {
                    window.location.href = '/login';
                    return;
                }
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                rawEntries = data.entries || [];
                document.getElementById('countAll').textContent = rawEntries.length;
                applyFiltersAndRender();
            } catch(e) {
                tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 24px; color: var(--danger);">Failed to load directory: ' + escapeHtml(e.message) + '</td></tr>';
            }
        }

        function updateBreadcrumbs() {
            const bar = document.getElementById('breadcrumbBar');
            const parts = currentPath.split('/').filter(p => p.length > 0);
            let html = '<span class="crumb-item" onclick="navigateTo(\'/\')">🏠 Root</span>';
            let acc = '';
            for (let i = 0; i < parts.length; i++) {
                acc += '/' + parts[i];
                const p = acc;
                const isLast = (i === parts.length - 1);
                html += '<span class="crumb-sep">/</span>';
                if (isLast) {
                    html += '<span class="crumb-current">' + escapeHtml(parts[i]) + '</span>';
                } else {
                    html += '<span class="crumb-item" onclick="navigateTo(\'' + escapeHtml(p) + '\')">' + escapeHtml(parts[i]) + '</span>';
                }
            }
            bar.innerHTML = html;
        }

        function navigateTo(path) {
            loadDirectory(path);
        }
        function refreshList() {
            loadDirectory(currentPath);
            loadDeviceInfo();
            showToast('Refreshed directory');
        }

        // Filters and Sorting
        function handleSearch(q) {
            searchQuery = (q || '').trim().toLowerCase();
            applyFiltersAndRender();
        }
        function setCategoryFilter(cat, btn) {
            categoryFilter = cat;
            document.querySelectorAll('.filter-pill').forEach(el => el.classList.remove('active'));
            if (btn) btn.classList.add('active');
            applyFiltersAndRender();
        }
        function sortTable(key) {
            if (sortKey === key) sortAsc = !sortAsc;
            else { sortKey = key; sortAsc = true; }
            applyFiltersAndRender();
        }

        function applyFiltersAndRender() {
            filteredEntries = rawEntries.filter(entry => {
                if (searchQuery && !entry.name.toLowerCase().includes(searchQuery)) return false;
                if (categoryFilter === 'DIR' && !entry.isDirectory) return false;
                if (categoryFilter === 'IMG') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return false;
                }
                if (categoryFilter === 'VID') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['mp4', 'mkv', 'webm', 'mov', 'avi', '3gp'].includes(ext)) return false;
                }
                if (categoryFilter === 'AUD') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) return false;
                }
                if (categoryFilter === 'DOC') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['pdf', 'doc', 'docx', 'txt', 'rtf', 'xlsx', 'xls', 'csv', 'ppt', 'pptx'].includes(ext)) return false;
                }
                if (categoryFilter === 'ARCHIVE') {
                    if (entry.isDirectory) return false;
                    const ext = getExt(entry.name);
                    if (!['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'apk'].includes(ext)) return false;
                }
                return true;
            });

            filteredEntries.sort((a, b) => {
                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;
                let valA, valB;
                if (sortKey === 'size') { valA = a.sizeBytes || 0; valB = b.sizeBytes || 0; }
                else if (sortKey === 'modified') { valA = a.modifiedMillis || 0; valB = b.modifiedMillis || 0; }
                else { valA = a.name.toLowerCase(); valB = b.name.toLowerCase(); }
                if (valA < valB) return sortAsc ? -1 : 1;
                if (valA > valB) return sortAsc ? 1 : -1;
                return 0;
            });

            renderView();
        }

        function renderView() {
            if (viewMode === 'list') renderTableView();
            else renderGridView();
        }

        function setViewMode(mode) {
            viewMode = mode;
            localStorage.setItem('portly_view_mode', mode);
            document.getElementById('listViewBtn').classList.toggle('active', mode === 'list');
            document.getElementById('gridViewBtn').classList.toggle('active', mode === 'grid');
            document.getElementById('listViewContainer').style.display = mode === 'list' ? 'block' : 'none';
            document.getElementById('gridViewContainer').style.display = mode === 'grid' ? 'grid' : 'none';
            renderView();
        }

        function renderTableView() {
            const tb = document.getElementById('tableBody');
            if (filteredEntries.length === 0) {
                tb.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 32px; color: var(--text-muted);">No matching files found.</td></tr>';
                return;
            }
            let html = '';
            for (const item of filteredEntries) {
                const isChecked = selectedPaths.has(item.path);
                const icon = item.isDirectory ? '📁' : getFileIcon(item.name);
                const ext = getExt(item.name) || (item.isDirectory ? 'FOLDER' : 'FILE');
                html += '<tr>' +
                    '<td style="text-align:center;"><input type="checkbox" ' + (isChecked ? 'checked' : '') + ' onchange="toggleSelect(\'' + escapeHtml(item.path) + '\', this.checked)" /></td>' +
                    '<td><div class="file-name-cell"><span class="file-icon">' + icon + '</span>' +
                        (item.isDirectory
                            ? '<span class="file-link" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">' + escapeHtml(item.name) + '</span>'
                            : '<span class="file-link" onclick="openPreview(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')">' + escapeHtml(item.name) + '</span>'
                        ) +
                    '</div></td>' +
                    '<td>' + (item.isDirectory ? '--' : (item.sizeFormatted || formatBytes(item.sizeBytes))) + '</td>' +
                    '<td>' + (item.modifiedFormatted || formatDate(item.modifiedMillis)) + '</td>' +
                    '<td><span class="file-badge">' + ext + '</span></td>' +
                    '<td style="text-align:right; white-space:nowrap;">' +
                        (item.isDirectory
                            ? '<button type="button" class="btn" style="padding:4px 8px;" onclick="navigateTo(\'' + escapeHtml(item.path) + '\')">Open</button> '
                            : '<button type="button" class="btn" style="padding:4px 8px;" onclick="openPreview(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')" title="Preview">👁️</button> ' +
                              '<a href="/download?path=' + encodeURIComponent(item.path) + '" class="btn" style="padding:4px 8px;" download title="Download">⬇️</a> '
                        ) +
                        (!isReadOnly ? '<button type="button" class="btn" style="padding:4px 8px;" onclick="openRenameModal(\'' + escapeHtml(item.path) + '\', \'' + escapeHtml(item.name) + '\')" title="Rename">✏️</button> ' : '') +
                        (!isReadOnly ? '<button type="button" class="btn" style="padding:4px 8px; color:var(--danger);" onclick="deleteItemSingle(\'' + escapeHtml(item.path) + '\')" title="Delete">🗑️</button>' : '') +
                    '</td>' +
                '</tr>';
            }
            tb.innerHTML = html;
        }

        function renderGridView() {
            const g = document.getElementById('gridViewContainer');
            if (filteredEntries.length === 0) {
                g.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 48px; color: var(--text-muted);">No matching files found.</div>';
                return;
            }
            let html = '';
            for (const item of filteredEntries) {
                const isChecked = selectedPaths.has(item.path);
                const icon = item.isDirectory ? '📁' : getFileIcon(item.name);
                html += '<div class="grid-card" onclick="handleCardClick(event, \'' + escapeHtml(item.path) + '\', ' + item.isDirectory + ', \'' + escapeHtml(item.name) + '\')">' +
                    '<input type="checkbox" class="grid-check" ' + (isChecked ? 'checked' : '') + ' onclick="event.stopPropagation(); toggleSelect(\'' + escapeHtml(item.path) + '\', this.checked)" />' +
                    '<div class="grid-icon">' + icon + '</div>' +
                    '<div class="grid-name" title="' + escapeHtml(item.name) + '">' + escapeHtml(item.name) + '</div>' +
                    '<div class="grid-meta">' + (item.isDirectory ? 'Folder' : (item.sizeFormatted || formatBytes(item.sizeBytes))) + '</div>' +
                '</div>';
            }
            g.innerHTML = html;
        }

        function handleCardClick(e, path, isDir, name) {
            if (isDir) navigateTo(path);
            else openPreview(path, name);
        }

        // Selection & Batch Actions
        function toggleSelect(path, checked) {
            if (checked) selectedPaths.add(path);
            else selectedPaths.delete(path);
            updateBatchBar();
        }
        function toggleSelectAll(checked) {
            if (checked) {
                filteredEntries.forEach(e => selectedPaths.add(e.path));
            } else {
                selectedPaths.clear();
            }
            renderView();
            updateBatchBar();
        }
        function clearSelection() {
            selectedPaths.clear();
            const el = document.getElementById('selectAllCheckbox');
            if (el) el.checked = false;
            renderView();
            updateBatchBar();
        }
        function updateBatchBar() {
            const bar = document.getElementById('batchBar');
            const cnt = selectedPaths.size;
            if (cnt > 0) {
                bar.style.display = 'flex';
                document.getElementById('batchCountText').textContent = cnt + ' selected';
            } else {
                bar.style.display = 'none';
            }
        }

        function downloadSelectedZip() {
            if (selectedPaths.size === 0) return;
            const p = Array.from(selectedPaths).join(',');
            window.location.href = '/api/zip-download?paths=' + encodeURIComponent(p);
            showToast('Preparing ZIP download...');
        }

        async function deleteSelectedBatch() {
            if (selectedPaths.size === 0) return;
            if (!confirm('Permanently delete ' + selectedPaths.size + ' selected item(s)?')) return;
            try {
                const res = await fetch('/api/batch-delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ paths: Array.from(selectedPaths) })
                });
                if (res.ok) {
                    const d = await res.json();
                    showToast('Deleted ' + d.successCount + ' item(s)');
                    clearSelection();
                    refreshList();
                } else {
                    showToast('Batch delete failed: ' + await res.text(), 'error');
                }
            } catch(e) {
                showToast('Network error: ' + e.message, 'error');
            }
        }

        // File Previews
        function openPreview(path, name) {
            const ext = getExt(name);
            const content = document.getElementById('previewContent');
            const title = document.getElementById('previewTitle');
            const dl = document.getElementById('previewDownloadBtn');
            title.textContent = name;
            dl.href = '/download?path=' + encodeURIComponent(path);

            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) {
                content.innerHTML = '<img src="/preview?path=' + encodeURIComponent(path) + '" style="max-width:100%; max-height:75vh; border-radius:8px; object-fit:contain;" />';
            } else if (['mp4', 'webm', 'mkv', 'mov'].includes(ext)) {
                content.innerHTML = '<video controls autoplay style="max-width:100%; max-height:75vh; border-radius:8px;"><source src="/preview?path=' + encodeURIComponent(path) + '"></video>';
            } else if (['mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac'].includes(ext)) {
                content.innerHTML = '<div style="padding:24px; width:100%;"><div style="font-size:48px; margin-bottom:12px;">🎵</div><audio controls autoplay style="width:100%;"><source src="/preview?path=' + encodeURIComponent(path) + '"></audio></div>';
            } else if (['pdf'].includes(ext)) {
                content.innerHTML = '<iframe src="/preview?path=' + encodeURIComponent(path) + '" style="width:100%; height:75vh; border:none; border-radius:8px;"></iframe>';
            } else if (['txt', 'log', 'json', 'xml', 'md', 'py', 'kt', 'java', 'html', 'css', 'js', 'sh'].includes(ext)) {
                content.innerHTML = '<div style="padding:24px;">Loading text content...</div>';
                fetch('/preview?path=' + encodeURIComponent(path))
                    .then(r => r.text())
                    .then(txt => {
                        content.innerHTML = '<pre style="text-align:left; background:var(--bg-card); padding:16px; border-radius:8px; overflow:auto; max-height:70vh; font-family:monospace; font-size:12px; width:100%;"><code>' + escapeHtml(txt) + '</code></pre>';
                    })
                    .catch(err => { content.innerHTML = 'Error loading text: ' + escapeHtml(err.message); });
            } else {
                content.innerHTML = '<div style="padding:32px;"><div style="font-size:48px; margin-bottom:12px;">📄</div><p>No inline preview available for this file type.</p><a href="/download?path=' + encodeURIComponent(path) + '" class="btn btn-primary" style="margin-top:16px;" download>Download File</a></div>';
            }
            document.getElementById('previewModal').style.display = 'flex';
        }

        // Upload execution
        function handleFileSelect(e) {
            const files = Array.from(e.target.files || []);
            if (files.length === 0) return;
            startUpload(files);
            e.target.value = '';
        }

        function startUpload(files) {
            const drawer = document.getElementById('uploadDrawer');
            const title = document.getElementById('uploadDrawerTitle');
            const fill = document.getElementById('uploadProgressFill');
            const bytesText = document.getElementById('uploadBytesText');
            const speedText = document.getElementById('uploadSpeedText');

            drawer.style.display = 'block';
            title.textContent = 'Uploading ' + files.length + ' file(s)...';
            fill.style.width = '0%';

            const fd = new FormData();
            files.forEach(f => fd.append('file', f, f.webkitRelativePath || f.name));

            const start = Date.now();
            activeXhr = new XMLHttpRequest();
            activeXhr.open('POST', '/api/upload?path=' + encodeURIComponent(currentPath));

            activeXhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    const pct = Math.round((e.loaded / e.total) * 100);
                    fill.style.width = pct + '%';
                    bytesText.textContent = formatBytes(e.loaded) + ' / ' + formatBytes(e.total);
                    const elapsed = (Date.now() - start) / 1000;
                    if (elapsed > 0.3) {
                        const speed = (e.loaded / (1024 * 1024 * elapsed)).toFixed(2);
                        speedText.textContent = speed + ' MB/s';
                    }
                }
            };

            activeXhr.onload = function() {
                activeXhr = null;
                if (this.status >= 200 && this.status < 300) {
                    fill.style.width = '100%';
                    title.textContent = 'Upload complete!';
                    showToast('Uploaded ' + files.length + ' file(s)');
                    setTimeout(() => { drawer.style.display = 'none'; }, 1600);
                    refreshList();
                } else {
                    title.textContent = 'Upload failed!';
                    showToast('Upload error: ' + this.responseText, 'error');
                }
            };

            activeXhr.onerror = function() {
                activeXhr = null;
                title.textContent = 'Upload interrupted!';
                showToast('Network error during upload', 'error');
            };

            activeXhr.send(fd);
        }

        function cancelUpload() {
            if (activeXhr) {
                activeXhr.abort();
                activeXhr = null;
                document.getElementById('uploadDrawer').style.display = 'none';
                showToast('Upload cancelled');
            }
        }

        // Drag & Drop
        const dz = document.getElementById('dropzone');
        if (dz) {
            ['dragenter', 'dragover'].forEach(n => {
                window.addEventListener(n, e => { e.preventDefault(); e.stopPropagation(); dz.classList.add('dragover'); });
            });
            ['dragleave', 'drop'].forEach(n => {
                window.addEventListener(n, e => { e.preventDefault(); e.stopPropagation(); dz.classList.remove('dragover'); });
            });
            window.addEventListener('drop', e => {
                const dt = e.dataTransfer;
                if (dt && dt.files && dt.files.length > 0) {
                    startUpload(Array.from(dt.files));
                }
            });
        }

        // Folder & Rename Modals
        function openNewFolderModal() {
            document.getElementById('newFolderName').value = '';
            document.getElementById('newFolderModal').style.display = 'flex';
            setTimeout(() => document.getElementById('newFolderName').focus(), 50);
        }
        async function submitCreateFolder() {
            const name = document.getElementById('newFolderName').value.trim();
            if (!name) return;
            closeModal('newFolderModal');
            try {
                const res = await fetch('/api/mkdir', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ parentPath: currentPath, name: name })
                });
                if (res.ok) {
                    showToast('Directory created');
                    refreshList();
                } else {
                    showToast('Failed to create folder: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        function openRenameModal(path, oldName) {
            renameTargetPath = path;
            const input = document.getElementById('renameNewName');
            input.value = oldName;
            document.getElementById('renameModal').style.display = 'flex';
            setTimeout(() => input.focus(), 50);
        }
        async function submitRename() {
            const newName = document.getElementById('renameNewName').value.trim();
            if (!newName || !renameTargetPath) return;
            closeModal('renameModal');
            try {
                const res = await fetch('/api/rename', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: renameTargetPath, newName: newName })
                });
                if (res.ok) {
                    showToast('Item renamed');
                    refreshList();
                } else {
                    showToast('Rename failed: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        async function deleteItemSingle(path) {
            if (!confirm('Permanently delete "' + path + '"?')) return;
            try {
                const res = await fetch('/api/delete', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: path })
                });
                if (res.ok) {
                    showToast('Item deleted');
                    refreshList();
                } else {
                    showToast('Delete failed: ' + await res.text(), 'error');
                }
            } catch(e) { showToast('Network error: ' + e.message, 'error'); }
        }

        function closeModal(id) {
            document.getElementById(id).style.display = 'none';
        }

        // Helper utilities
        function formatBytes(b) {
            if (!b || b === 0) return '0 B';
            const k = 1024, s = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(b) / Math.log(k));
            return parseFloat((b / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
        }
        function formatDate(ms) {
            if (!ms) return '--';
            const d = new Date(ms);
            return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0') + ' ' + String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
        }
        function getExt(n) {
            const idx = n.lastIndexOf('.');
            return idx > 0 ? n.substring(idx + 1).toLowerCase() : '';
        }
        function getFileIcon(n) {
            const ext = getExt(n);
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return '🖼️';
            if (['mp4', 'mkv', 'webm', 'mov'].includes(ext)) return '🎬';
            if (['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(ext)) return '🎵';
            if (['pdf'].includes(ext)) return '📕';
            if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '📦';
            if (['apk'].includes(ext)) return '🤖';
            if (['doc', 'docx', 'txt', 'rtf'].includes(ext)) return '📄';
            if (['xls', 'xlsx', 'csv'].includes(ext)) return '📊';
            return '📄';
        }
        function escapeHtml(s) {
            if (!s) return '';
            return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
        }
        function showToast(msg, type) {
            const tc = document.getElementById('toastContainer');
            const el = document.createElement('div');
            el.className = 'toast-item';
            el.innerHTML = (type === 'error' ? '⚠️ ' : '✅ ') + escapeHtml(msg);
            tc.appendChild(el);
            setTimeout(() => { el.remove(); }, 3200);
        }

        // Keybindings
        window.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                closeModal('previewModal');
                closeModal('newFolderModal');
                closeModal('renameModal');
            }
        });

        loadDirectory(currentPath);
    </script>
</body>
</html>
""".trimIndent()
}
