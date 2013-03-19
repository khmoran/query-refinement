
#export JAVA_OPTS='-Xmx=4096'
export JAVA_OPTS='-Xmx=4096 -XX:+UseG1GC'                                                                                                                                                                                                    

mvn test -Dtest=edu.tufts.cs.ebm.review.systematic.SimulateReviewProtonBeam