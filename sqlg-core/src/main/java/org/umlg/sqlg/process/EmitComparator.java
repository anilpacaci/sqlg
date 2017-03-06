package org.umlg.sqlg.process;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.lambda.ElementValueTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.lambda.IdentityTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.SelectOneStep;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.javatuples.Pair;
import org.umlg.sqlg.sql.parse.ReplacedStep;
import org.umlg.sqlg.strategy.Emit;
import org.umlg.sqlg.structure.SqlgElement;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Pieter Martin (https://github.com/pietermartin)
 *         Date: 2017/02/24
 */
public class EmitComparator implements Comparator<Emit> {

    private List<ReplacedStep<?, ?>> replacedSteps;

    public EmitComparator(List<ReplacedStep<?, ?>> replacedSteps) {
        this.replacedSteps = replacedSteps;
    }

    /**
     * We start comparing from the leaf nodes. i.e. from the outside in.
     */
    @Override
    public int compare(Emit emit1, Emit emit2) {
        //Only the last comparator in the 2 list matters.
        //It overrides the previous ones.
        List<Pair<Traversal.Admin, Comparator>> pathComparator1 = (List<Pair<Traversal.Admin, Comparator>>) emit1.getPathComparators().get(emit1.getPathComparators().size() - 1);
        List<Pair<Traversal.Admin, Comparator>> pathComparator2 = (List<Pair<Traversal.Admin, Comparator>>) emit2.getPathComparators().get(emit2.getPathComparators().size() - 1);
        int comparatorCount = 0;
        for (Pair<Traversal.Admin, Comparator> comparatorPair1 : pathComparator1) {
            if (pathComparator2.size() <= comparatorCount) {
                return -1;
            }
            Pair<Traversal.Admin, Comparator> comparatorPair2 = pathComparator2.get(comparatorCount);
            Traversal.Admin traversal1 = comparatorPair1.getValue0();
            Comparator comparator1 = comparatorPair1.getValue1();
            Traversal.Admin traversal2 = comparatorPair2.getValue0();
            Comparator comparator2 = comparatorPair2.getValue1();

            if (traversal1 instanceof ElementValueTraversal) {
                SqlgElement sqlgElement1 = (SqlgElement) emit1.getPath().objects().get(emit1.getPath().objects().size() - 1);
                SqlgElement sqlgElement2 = (SqlgElement) emit2.getPath().objects().get(emit2.getPath().objects().size() - 1);
                if (sqlgElement1 != null && sqlgElement2 != null) {
                    ElementValueTraversal elementValueTraversal = (ElementValueTraversal) traversal1;
                    int compare = comparator1.compare(sqlgElement1.value(elementValueTraversal.getPropertyKey()), sqlgElement2.value(elementValueTraversal.getPropertyKey()));
                    if (compare != 0) {
                        return compare;
                    }
                }
            } else if (traversal1.getSteps().size() == 1 && traversal1.getSteps().get(0) instanceof SelectOneStep) {
                //xxxxx.select("a").order().by(select("a").by("name"), Order.decr)
                SelectOneStep selectOneStep = (SelectOneStep) traversal1.getSteps().get(0);
                Preconditions.checkState(selectOneStep.getScopeKeys().size() == 1, "toOrderByClause expects the selectOneStep to have one scopeKey!");
                Preconditions.checkState(selectOneStep.getLocalChildren().size() == 1, "toOrderByClause expects the selectOneStep to have one traversal!");
                Preconditions.checkState(selectOneStep.getLocalChildren().get(0) instanceof ElementValueTraversal, "toOrderByClause expects the selectOneStep's traversal to be a ElementValueTraversal!");

                SqlgElement sqlgElement1 = null;
                SqlgElement sqlgElement2 = null;
                String selectKey = (String) selectOneStep.getScopeKeys().iterator().next();
                //Go up the path to find the element with the selectKey label
                int pathCount = 0;
                boolean foundLabel = false;
                for (Set<String> labels : emit1.getPath().labels()) {
                    if (labels.contains(selectKey)) {
                        foundLabel = true;
                        break;
                    }
                    pathCount++;
                }
                if (foundLabel) {
                    sqlgElement1 = (SqlgElement) emit1.getPath().objects().get(pathCount);
                    if (emit2.getPath().objects().size() <= pathCount) {
                        return -1;
                    }
                    sqlgElement2 = (SqlgElement) emit2.getPath().objects().get(pathCount);
                }

                if (sqlgElement1 != null && sqlgElement2 != null) {
                    ElementValueTraversal elementValueTraversal = (ElementValueTraversal) selectOneStep.getLocalChildren().get(0);
                    Property sqlgElement1Property = sqlgElement1.property(elementValueTraversal.getPropertyKey());
                    Property sqlgElement2Property = sqlgElement2.property(elementValueTraversal.getPropertyKey());
                    if (sqlgElement1Property.isPresent() && !sqlgElement2Property.isPresent()) {
                        return 1;
                    } else if (!sqlgElement1Property.isPresent() && sqlgElement2Property.isPresent()) {
                        return -1;
                    } else if (!sqlgElement1Property.isPresent() && !sqlgElement2Property.isPresent()) {
                        return 0;
                    } else {
                        int compare = comparator1.compare(sqlgElement1.value(elementValueTraversal.getPropertyKey()), sqlgElement2.value(elementValueTraversal.getPropertyKey()));
                        if (compare != 0) {
                            return compare;
                        }
                    }
                }
            } else if (traversal1 instanceof IdentityTraversal) {
                return Order.shuffle.compare(null, null);
            } else {
                throw new IllegalStateException("Unhandled order traversal " + traversal1.toString());
            }
            comparatorCount++;
        }
        return 0;
    }

//    /**
//     * We start comparing from the leaf nodes. i.e. from the outside in.
//     */
//    @Override
//    public int compare(Emit emit1, Emit emit2) {
//
//        //Only the last comparator in the 2 list matters.
//        //It overrides the previous ones.
//        List<List<Pair<Traversal.Admin, Comparator>>> pathComparators1 = emit1.getPathComparators();
//        List<List<Pair<Traversal.Admin, Comparator>>> pathComparators2 = emit2.getPathComparators();
//        int count = 0;
//        for (List<Pair<Traversal.Admin, Comparator>> pathComparator1 : pathComparators1) {
//
//            //The paths are not necessarily the same length.
//            if (pathComparators2.size() <= count) {
//                return -1;
//            }
//            List<Pair<Traversal.Admin, Comparator>> pathComparator2 = pathComparators2.get(count);
//
//            int comparatorCount = 0;
//            for (Pair<Traversal.Admin, Comparator> comparatorPair1 : pathComparator1) {
//
//                Pair<Traversal.Admin, Comparator> comparatorPair2 = pathComparator2.get(comparatorCount);
//
//                Traversal.Admin traversal1 = comparatorPair1.getValue0();
//                Comparator comparator1 = comparatorPair1.getValue1();
//
//                Traversal.Admin traversal2 = comparatorPair2.getValue0();
//                Comparator comparator2 = comparatorPair2.getValue1();
//
//                if (traversal1 instanceof ElementValueTraversal) {
//                    SqlgElement sqlgElement1 = (SqlgElement) emit1.getPath().objects().get(count);
//                    SqlgElement sqlgElement2 = (SqlgElement) emit2.getPath().objects().get(count);
//                    if (sqlgElement1 != null && sqlgElement2 != null) {
//                        ElementValueTraversal elementValueTraversal = (ElementValueTraversal) traversal1;
//                        int compare = comparator1.compare(sqlgElement1.value(elementValueTraversal.getPropertyKey()), sqlgElement2.value(elementValueTraversal.getPropertyKey()));
//                        if (compare != 0) {
//                            return compare;
//                        }
//                    }
//                } else if (traversal1.getSteps().size() == 1 && traversal1.getSteps().get(0) instanceof SelectOneStep) {
//                    //xxxxx.select("a").order().by(select("a").by("name"), Order.decr)
//                    SelectOneStep selectOneStep = (SelectOneStep) traversal1.getSteps().get(0);
//                    Preconditions.checkState(selectOneStep.getScopeKeys().size() == 1, "toOrderByClause expects the selectOneStep to have one scopeKey!");
//                    Preconditions.checkState(selectOneStep.getLocalChildren().size() == 1, "toOrderByClause expects the selectOneStep to have one traversal!");
//                    Preconditions.checkState(selectOneStep.getLocalChildren().get(0) instanceof ElementValueTraversal, "toOrderByClause expects the selectOneStep's traversal to be a ElementValueTraversal!");
//
//                    SqlgElement sqlgElement1 = null;
//                    SqlgElement sqlgElement2 = null;
//                    String selectKey = (String) selectOneStep.getScopeKeys().iterator().next();
//                    //Go up the path to find the element with the selectKey label
//                    int pathCount = 0;
//                    boolean foundLabel = false;
//                    for (Set<String> labels : emit1.getPath().labels()) {
//                        if (labels.contains(selectKey)) {
//                            foundLabel = true;
//                            break;
//                        }
//                        pathCount++;
//                    }
//                    if (foundLabel) {
//                        sqlgElement1 = (SqlgElement) emit1.getPath().objects().get(pathCount);
//                        sqlgElement2 = (SqlgElement) emit2.getPath().objects().get(pathCount);
//                    }
//
//                    if (sqlgElement1 != null && sqlgElement2 != null) {
//                        ElementValueTraversal elementValueTraversal = (ElementValueTraversal) selectOneStep.getLocalChildren().get(0);
//                        Property sqlgElement1Property = sqlgElement1.property(elementValueTraversal.getPropertyKey());
//                        Property sqlgElement2Property = sqlgElement2.property(elementValueTraversal.getPropertyKey());
//                        if (sqlgElement1Property.isPresent() && !sqlgElement2Property.isPresent()) {
//                            return 1;
//                        } else if (!sqlgElement1Property.isPresent() && sqlgElement2Property.isPresent()) {
//                            return -1;
//                        } else if (!sqlgElement1Property.isPresent() && !sqlgElement2Property.isPresent()) {
//                            return 0;
//                        } else {
//                            int compare = comparator1.compare(sqlgElement1.value(elementValueTraversal.getPropertyKey()), sqlgElement2.value(elementValueTraversal.getPropertyKey()));
//                            if (compare != 0) {
//                                return compare;
//                            }
//                        }
//                    }
//                } else if (traversal1 instanceof IdentityTraversal) {
//                    return Order.shuffle.compare(null, null);
//                } else {
//                    throw new IllegalStateException("Unhandled order traversal " + traversal1.toString());
//                }
//                comparatorCount++;
//            }
//            count++;
//        }
//        return 0;
//    }

}