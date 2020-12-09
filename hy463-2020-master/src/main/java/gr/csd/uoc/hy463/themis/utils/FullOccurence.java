package gr.csd.uoc.hy463.themis.utils;

/**
 * Class created to store occurrences.
 * String field => On which field this word appeared in.
 * Int occurNum => How many times it appeared in this field.
 */
public class FullOccurence {

    private String field;
    private int tf;

    public FullOccurence() { setField("");setTf(0);}
    public FullOccurence(String field,int num){ setField(field);setTf(num);}

    public void setField(String field)
    {
        this.field = field;
    }

    public String getField()
    {
        return this.field;
    }

    public void setTf(int num)
    {
        this.tf = num;
    }

    public int getTf()
    {
        return this.tf;
    }

    public String toString()
    {
        return getField() + "=>" + getTf();
    }


}
