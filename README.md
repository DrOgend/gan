# gan
GAN = Gatling ANalyzer

Utility for automatic analysis of Load Testing results got by Gatling and collected in InfluxDB

Using examples:

Example 1: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 -  print transaction avg resp times
Example 2: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 CHECK - check transaction avg times with sla.txt file, to satisfy SLA
Example 3: java -jar gan-assembly-1.0.jar ufr-mobileregistration-basescenario 201711211758 201711211900 SLA - generate SLA file: sla.txt this file have to be edite with max resp times allowed to SLA

where ufr-mobileregistration-basescenario - test_scenario name in InfluxDB 
(
in Gatling script we have next class simulation name:
class ufr_Mobileregistration_Basescenario extends Simulation {
) 
and 201711211758 201711211900 - start and end dates


